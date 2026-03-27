#!/usr/bin/env ruby

require "json"
require "net/http"
require "set"
require "uri"

class JiraSeeder
  def initialize
    @jira_base_url = fetch_env("JIRA_BASE_URL")
    @jira_email = fetch_env("JIRA_EMAIL")
    @jira_api_token = fetch_env("JIRA_API_TOKEN")
    @bandviz_base_url = ENV.fetch("BANDVIZ_BASE_URL", "http://localhost:8080")
    @default_role = ENV.fetch("BANDVIZ_DEFAULT_DEVELOPER_ROLE", "DEVELOPER")
    @default_capacity = Integer(ENV.fetch("BANDVIZ_DEFAULT_WEEKLY_CAPACITY", "40"))
    @target_utilization = Integer(ENV.fetch("BANDVIZ_DEFAULT_TARGET_UTILIZATION", "70"))
    @dry_run = ENV.fetch("DRY_RUN", "false").downcase == "true"
  end

  def run
    command = ARGV[0] || "seed-all"

    case command
    when "seed-all"
      seed_projects
      seed_developers
    when "seed-projects"
      seed_projects
    when "seed-developers"
      seed_developers
    else
      abort <<~USAGE
        Unknown command: #{command}

        Usage:
          ruby scripts/seed_from_jira.rb seed-all
          ruby scripts/seed_from_jira.rb seed-projects
          ruby scripts/seed_from_jira.rb seed-developers
      USAGE
    end
  end

  private

  def seed_projects
    jira_projects = jira_get("/rest/api/3/project/search?maxResults=100").fetch("values")
    existing_projects = bandviz_get("/api/projects?activeOnly=false")
    existing_by_key = existing_projects.each_with_object({}) do |project, acc|
      key = project["jiraProjectKey"]
      acc[key] = project if key && !key.empty?
    end

    created = 0
    updated = 0
    skipped = 0

    jira_projects.each do |project|
      key = project.fetch("key")
      payload = {
        name: project.fetch("name"),
        jiraProjectKey: key,
        targetUtilizationPct: @target_utilization
      }

      existing = existing_by_key[key]
      if existing
        if project_changed?(existing, payload)
          bandviz_put("/api/projects/#{existing.fetch('id')}", payload)
          updated += 1
        else
          skipped += 1
        end
      else
        bandviz_post("/api/projects", payload)
        created += 1
      end
    end

    puts format_summary("projects", jira_projects.length, created, updated, skipped)
  end

  def seed_developers
    org_id = fetch_env("ATLASSIAN_ORG_ID")
    site_id = fetch_env("ATLASSIAN_SITE_ID")
    team_ids = resolve_team_ids(org_id, site_id)
    account_ids = fetch_team_account_ids(org_id, site_id, team_ids)
    developers = account_ids.map { |account_id| jira_get("/rest/api/3/user?accountId=#{url_encode(account_id)}") }
    active_developers = developers.select { |user| user["active"] && present?(user["emailAddress"]) }

    existing_developers = bandviz_get("/api/developers?activeOnly=false")
    existing_by_email = existing_developers.each_with_object({}) do |developer, acc|
      acc[developer["email"]] = developer if present?(developer["email"])
    end

    created = 0
    updated = 0
    skipped = 0

    active_developers.each do |user|
      payload = {
        name: user.fetch("displayName"),
        email: user.fetch("emailAddress"),
        role: @default_role,
        weeklyCapacityHours: @default_capacity,
        jiraUsername: user.fetch("emailAddress")
      }

      existing = existing_by_email[payload[:email]]
      if existing
        if developer_changed?(existing, payload)
          bandviz_put("/api/developers/#{existing.fetch('id')}", payload)
          updated += 1
        else
          skipped += 1
        end
      else
        bandviz_post("/api/developers", payload)
        created += 1
      end
    end

    puts format_summary("developers", active_developers.length, created, updated, skipped)
  end

  def resolve_team_ids(org_id, site_id)
    configured_team_ids = csv_env("ATLASSIAN_TEAM_IDS")
    configured_team_names = csv_env("ATLASSIAN_TEAM_NAMES")

    if configured_team_ids.empty? && configured_team_names.empty?
      abort("Provide ATLASSIAN_TEAM_NAMES or ATLASSIAN_TEAM_IDS for developer seeding")
    end

    return configured_team_ids if configured_team_names.empty?

    teams = fetch_all_teams(org_id, site_id)
    teams_by_name = teams.group_by { |team| team["displayName"] }

    resolved_team_ids = configured_team_names.map do |team_name|
      matches = teams_by_name[team_name] || []

      if matches.empty?
        abort("No Atlassian team found with displayName '#{team_name}'")
      end

      if matches.length > 1
        team_ids = matches.map { |team| team["teamId"] }.join(", ")
        abort("Multiple Atlassian teams matched '#{team_name}': #{team_ids}")
      end

      matches.first.fetch("teamId")
    end

    (configured_team_ids + resolved_team_ids).uniq
  end

  def fetch_all_teams(org_id, site_id)
    teams = []
    cursor = nil

    loop do
      query = ["siteId=#{url_encode(site_id)}"]
      query << "cursor=#{url_encode(cursor)}" if present?(cursor)
      response = jira_get("/gateway/api/public/teams/v1/org/#{org_id}/teams?#{query.join('&')}")
      teams.concat(response.fetch("entities", []))
      cursor = response["cursor"]
      break unless present?(cursor)
    end

    teams
  end

  def fetch_team_account_ids(org_id, site_id, team_ids)
    account_ids = Set.new

    team_ids.each do |team_id|
      after = nil

      loop do
        payload = { first: 50 }
        payload[:after] = after if present?(after)
        response = jira_post_json(
          "/gateway/api/public/teams/v1/org/#{org_id}/teams/#{team_id}/members?siteId=#{site_id}",
          payload
        )

        response.fetch("results", []).each do |member|
          account_ids << member["accountId"] if present?(member["accountId"])
        end

        page_info = response.fetch("pageInfo", {})
        break unless page_info["hasNextPage"]

        after = page_info["endCursor"]
      end
    end

    account_ids.to_a
  end

  def project_changed?(existing, payload)
    existing["name"] != payload[:name] ||
      existing["jiraProjectKey"] != payload[:jiraProjectKey] ||
      existing["targetUtilizationPct"] != payload[:targetUtilizationPct]
  end

  def developer_changed?(existing, payload)
    existing["name"] != payload[:name] ||
      existing["role"] != payload[:role] ||
      existing["weeklyCapacityHours"] != payload[:weeklyCapacityHours] ||
      existing["jiraUsername"] != payload[:jiraUsername]
  end

  def format_summary(resource, total, created, updated, skipped)
    JSON.pretty_generate(
      resource: resource,
      total_from_jira: total,
      created: created,
      updated: updated,
      skipped: skipped,
      dry_run: @dry_run
    )
  end

  def fetch_env(name)
    value = ENV[name]
    return value if present?(value)

    abort("Missing required environment variable: #{name}")
  end

  def csv_env(name)
    ENV.fetch(name, "").split(",").map(&:strip).reject(&:empty?)
  end

  def present?(value)
    !value.nil? && !value.to_s.strip.empty?
  end

  def url_encode(value)
    URI.encode_www_form_component(value)
  end

  def jira_get(path)
    uri = URI.join(@jira_base_url, path)
    request = Net::HTTP::Get.new(uri)
    request["Accept"] = "application/json"
    request.basic_auth(@jira_email, @jira_api_token)
    json_request(uri, request, "Jira GET #{path}")
  end

  def jira_post_json(path, payload)
    uri = URI.join(@jira_base_url, path)
    request = Net::HTTP::Post.new(uri)
    request["Accept"] = "application/json"
    request["Content-Type"] = "application/json"
    request.basic_auth(@jira_email, @jira_api_token)
    request.body = JSON.generate(payload)
    json_request(uri, request, "Jira POST #{path}")
  end

  def bandviz_get(path)
    uri = URI.join(@bandviz_base_url, path)
    request = Net::HTTP::Get.new(uri)
    request["Accept"] = "application/json"
    json_request(uri, request, "BandViz GET #{path}")
  end

  def bandviz_post(path, payload)
    return payload if @dry_run

    uri = URI.join(@bandviz_base_url, path)
    request = Net::HTTP::Post.new(uri)
    request["Accept"] = "application/json"
    request["Content-Type"] = "application/json"
    request.body = JSON.generate(payload)
    json_request(uri, request, "BandViz POST #{path}")
  end

  def bandviz_put(path, payload)
    return payload if @dry_run

    uri = URI.join(@bandviz_base_url, path)
    request = Net::HTTP::Put.new(uri)
    request["Accept"] = "application/json"
    request["Content-Type"] = "application/json"
    request.body = JSON.generate(payload)
    json_request(uri, request, "BandViz PUT #{path}")
  end

  def json_request(uri, request, label)
    response = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == "https") do |http|
      http.request(request)
    end

    unless response.is_a?(Net::HTTPSuccess)
      abort("#{label} failed: #{response.code} #{response.body}")
    end

    body = response.body.to_s
    return {} if body.empty?

    JSON.parse(body)
  end
end

JiraSeeder.new.run
