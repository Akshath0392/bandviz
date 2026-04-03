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

    @atlassian_org_id = fetch_env("ATLASSIAN_ORG_ID")
    @atlassian_site_id = fetch_env("ATLASSIAN_SITE_ID")

    @bandviz_base_url = ENV.fetch("BANDVIZ_BASE_URL", "http://localhost:8080")
    @default_role = ENV.fetch("BANDVIZ_DEFAULT_DEVELOPER_ROLE", "DEVELOPER")
    @default_capacity = Integer(ENV.fetch("BANDVIZ_DEFAULT_WEEKLY_CAPACITY", "40"))
    @target_utilization = Integer(ENV.fetch("BANDVIZ_DEFAULT_TARGET_UTILIZATION", "70"))
    @default_delivery_mode = ENV.fetch("BANDVIZ_DEFAULT_DELIVERY_MODE", "HYBRID")

    @dry_run = ENV.fetch("DRY_RUN", "false").downcase == "true"
    @duplicate_user_strategy = ENV.fetch("ATLASSIAN_DUPLICATE_USER_TEAM_STRATEGY", "first").downcase

    @configured_team_ids = csv_env("ATLASSIAN_TEAM_IDS")
    @configured_team_names = csv_env("ATLASSIAN_TEAM_NAMES")
    @team_project_map = parse_team_project_map

    @selected_teams = nil
    @bandviz_team_by_name = {}
    @dry_run_next_team_id = -1
  end

  def run
    command = ARGV[0] || "seed-global"

    case command
    when "seed-global", "seed-all"
      sync_teams
      sync_projects
      sync_developers
    when "seed-teams"
      sync_teams
    when "seed-projects"
      sync_teams
      sync_projects
    when "seed-developers"
      sync_teams
      sync_developers
    else
      abort <<~USAGE
        Unknown command: #{command}

        Usage:
          ruby scripts/seed_from_jira.rb seed-global
          ruby scripts/seed_from_jira.rb seed-all
          ruby scripts/seed_from_jira.rb seed-teams
          ruby scripts/seed_from_jira.rb seed-projects
          ruby scripts/seed_from_jira.rb seed-developers
      USAGE
    end
  end

  private

  def sync_teams
    teams = selected_teams
    existing = bandviz_get("/api/teams?activeOnly=false")
    existing_by_name = existing.each_with_object({}) { |team, acc| acc[team.fetch("name")] = team }

    created = 0
    updated = 0
    skipped = 0

    teams.each do |team|
      name = team.fetch("displayName")
      payload = {
        name: name,
        description: "Synced from Atlassian team #{team.fetch('teamId')}"
      }

      if (row = existing_by_name[name])
        if row["description"] != payload[:description]
          bandviz_put("/api/teams/#{row.fetch('id')}", payload)
          updated += 1
        else
          skipped += 1
        end
        @bandviz_team_by_name[name] = row.merge("description" => payload[:description])
      else
        created_row = bandviz_post("/api/teams", payload)
        created += 1
        if @dry_run
          created_row = created_row.merge("id" => @dry_run_next_team_id)
          @dry_run_next_team_id -= 1
        end
        @bandviz_team_by_name[name] = created_row
      end
    end

    puts summary("teams", teams.length, created, updated, skipped)
  end

  def sync_projects
    ensure_bandviz_teams_loaded

    jira_projects = fetch_all_jira_projects
    jira_by_key = jira_projects.each_with_object({}) { |project, acc| acc[project.fetch("key")] = project }

    existing_projects = bandviz_get("/api/projects?activeOnly=false")
    existing_by_key = existing_projects.each_with_object({}) do |project, acc|
      key = project["jiraProjectKey"]
      acc[key] = project if present?(key)
    end

    created = 0
    updated = 0
    skipped = 0
    project_key_to_team_names = Hash.new { |hash, key| hash[key] = [] }

    selected_teams.each do |team|
      team_name = team.fetch("displayName")
      mapped_project_keys_for_team(team).each do |project_key|
        project_key_to_team_names[project_key] << team_name
      end
    end

    project_key_to_team_names.each do |project_key, team_names|
      jira_project = jira_by_key[project_key]
      unless jira_project
        abort("Jira project key '#{project_key}' was mapped but was not returned by Jira project search")
      end

      unique_team_names = team_names.uniq
      bandviz_teams = unique_team_names.map do |team_name|
        team = @bandviz_team_by_name[team_name]
        abort("BandViz team not found for '#{team_name}'. Run seed-teams first.") if team.nil?
        team
      end

      owner_team_id = bandviz_teams.first.fetch("id")
      permitted_team_ids = bandviz_teams.map { |team| team.fetch("id") }.uniq

      payload = {
        name: jira_project.fetch("name"),
        jiraProjectKey: jira_project.fetch("key"),
        targetUtilizationPct: @target_utilization,
        deliveryMode: @default_delivery_mode,
        teamId: owner_team_id,
        permittedTeamIds: permitted_team_ids
      }

      existing = existing_by_key[project_key]
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

    puts summary("projects", project_key_to_team_names.keys.length, created, updated, skipped)
  end

  def sync_developers
    ensure_bandviz_teams_loaded

    account_ids_by_team = selected_teams.each_with_object({}) do |team, acc|
      acc[team.fetch("displayName")] = fetch_team_account_ids(team.fetch("teamId"))
    end

    existing_developers = bandviz_get("/api/developers?activeOnly=false")
    existing_by_email = existing_developers.each_with_object({}) do |developer, acc|
      email = developer["email"]
      acc[email] = developer if present?(email)
    end

    created = 0
    updated = 0
    skipped = 0
    seen_emails = Set.new

    account_ids_by_team.each do |team_name, account_ids|
      bandviz_team = @bandviz_team_by_name[team_name]
      team_id = bandviz_team.fetch("id")

      account_ids.each do |account_id|
        user = jira_get("/rest/api/3/user?accountId=#{url_encode(account_id)}")
        next unless user["active"]

        email = user["emailAddress"]
        unless present?(email)
          warn "Skipping account #{account_id} because emailAddress is not available"
          next
        end

        if seen_emails.include?(email)
          case @duplicate_user_strategy
          when "first"
            next
          when "error"
            abort("User #{email} appears in multiple Jira teams. Set ATLASSIAN_DUPLICATE_USER_TEAM_STRATEGY=first to keep first mapping.")
          else
            abort("Unsupported ATLASSIAN_DUPLICATE_USER_TEAM_STRATEGY: #{@duplicate_user_strategy}")
          end
        end

        seen_emails << email

        payload = {
          name: user.fetch("displayName"),
          email: email,
          role: @default_role,
          weeklyCapacityHours: @default_capacity,
          jiraUsername: email,
          teamId: team_id
        }

        existing = existing_by_email[email]
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
    end

    puts summary("developers", seen_emails.size, created, updated, skipped)
  end

  def selected_teams
    return @selected_teams if @selected_teams

    if @configured_team_ids.empty? && @configured_team_names.empty?
      abort("Set ATLASSIAN_TEAM_NAMES or ATLASSIAN_TEAM_IDS")
    end

    all = fetch_all_teams
    by_id = all.each_with_object({}) { |team, acc| acc[team.fetch("teamId")] = team }
    by_name = all.group_by { |team| team.fetch("displayName") }

    selected = []

    @configured_team_ids.each do |team_id|
      team = by_id[team_id]
      abort("No Atlassian team found for id '#{team_id}'") unless team
      selected << team
    end

    @configured_team_names.each do |team_name|
      matches = by_name[team_name] || []
      if matches.empty?
        abort("No Atlassian team found for displayName '#{team_name}'")
      end
      if matches.size > 1
        ids = matches.map { |row| row.fetch("teamId") }.join(", ")
        abort("Multiple Atlassian teams matched '#{team_name}': #{ids}")
      end
      selected << matches.first
    end

    @selected_teams = selected.uniq { |team| team.fetch("teamId") }
  end

  def fetch_all_jira_projects
    all = []
    start_at = 0

    loop do
      response = jira_get("/rest/api/3/project/search?maxResults=100&startAt=#{start_at}")
      values = response.fetch("values", [])
      all.concat(values)
      start_at += values.length
      break if start_at >= response.fetch("total", 0)
      break if values.empty?
    end

    all
  end

  def fetch_all_teams
    teams = []
    cursor = nil

    loop do
      query = ["siteId=#{url_encode(@atlassian_site_id)}"]
      query << "cursor=#{url_encode(cursor)}" if present?(cursor)
      response = jira_get("/gateway/api/public/teams/v1/org/#{@atlassian_org_id}/teams?#{query.join('&')}")
      teams.concat(response.fetch("entities", []))
      cursor = response["cursor"]
      break unless present?(cursor)
    end

    teams
  end

  def fetch_team_account_ids(team_id)
    account_ids = Set.new
    after = nil

    loop do
      payload = { first: 50 }
      payload[:after] = after if present?(after)
      response = jira_post_json(
        "/gateway/api/public/teams/v1/org/#{@atlassian_org_id}/teams/#{team_id}/members?siteId=#{@atlassian_site_id}",
        payload
      )

      response.fetch("results", []).each do |member|
        account_ids << member["accountId"] if present?(member["accountId"])
      end

      page_info = response.fetch("pageInfo", {})
      break unless page_info["hasNextPage"]

      after = page_info["endCursor"]
    end

    account_ids.to_a
  end

  def mapped_project_keys_for_team(team)
    team_id = team.fetch("teamId")
    team_name = team.fetch("displayName")

    keys = @team_project_map[team_id] || @team_project_map[team_name]
    if keys.nil? || keys.empty?
      abort("No project mapping configured for Atlassian team '#{team_name}' (#{team_id}). Set ATLASSIAN_TEAM_PROJECT_KEYS_JSON.")
    end

    keys
  end

  def ensure_bandviz_teams_loaded
    return unless @bandviz_team_by_name.empty?

    teams = bandviz_get("/api/teams?activeOnly=false")
    @bandviz_team_by_name = teams.each_with_object({}) { |team, acc| acc[team.fetch("name")] = team }
  end

  def parse_team_project_map
    raw = ENV.fetch("ATLASSIAN_TEAM_PROJECT_KEYS_JSON", "").strip
    return {} if raw.empty?

    parsed = JSON.parse(raw)
    unless parsed.is_a?(Hash)
      abort("ATLASSIAN_TEAM_PROJECT_KEYS_JSON must be a JSON object")
    end

    parsed.each_with_object({}) do |(team_key, project_keys), acc|
      unless project_keys.is_a?(Array) && project_keys.all? { |item| present?(item) }
        abort("Mapping for '#{team_key}' must be an array of Jira project keys")
      end
      acc[team_key.to_s] = project_keys.map(&:to_s).map(&:strip).reject(&:empty?).uniq
    end
  rescue JSON::ParserError => e
    abort("Failed to parse ATLASSIAN_TEAM_PROJECT_KEYS_JSON: #{e.message}")
  end

  def project_changed?(existing, payload)
    existing["name"] != payload[:name] ||
      existing["jiraProjectKey"] != payload[:jiraProjectKey] ||
      existing["targetUtilizationPct"] != payload[:targetUtilizationPct] ||
      existing["deliveryMode"] != payload[:deliveryMode] ||
      existing["teamId"] != payload[:teamId] ||
      normalized_team_ids(existing["permittedTeamIds"]) != normalized_team_ids(payload[:permittedTeamIds])
  end

  def normalized_team_ids(values)
    Array(values).map(&:to_i).uniq.sort
  end

  def developer_changed?(existing, payload)
    existing["name"] != payload[:name] ||
      existing["role"] != payload[:role] ||
      existing["weeklyCapacityHours"] != payload[:weeklyCapacityHours] ||
      existing["jiraUsername"] != payload[:jiraUsername] ||
      existing["teamId"] != payload[:teamId]
  end

  def summary(resource, total, created, updated, skipped)
    JSON.pretty_generate(
      resource: resource,
      total_source: total,
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
