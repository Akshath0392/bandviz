# BandViz

BandViz is a web-based post-sale bandwidth management application for engineering managers.

It provides:
- Team bandwidth and utilization view
- Developer-level allocation and Jira ticket load
- Leave approval workflow and calendar
- Bandwidth planner for project allocations
- Jira sync status and on-demand sync
- Team-based ownership mapping for projects and developers

BandViz now supports mixed delivery models:
- Sprint teams can plan against an active sprint
- Kanban teams can plan against a rolling planning window
- Projects can be marked as `SPRINT`, `KANBAN`, or `HYBRID`

Ownership hierarchy:
- `Project (primary team + permitted teams) -> Team -> Developer -> Tickets`
- Projects have one primary team owner and one or more permitted teams
- Developers can be tagged to a team
- Jira tickets can then be reviewed by developer, project, or team mapping

## Tech Stack

- Java 25
- Spring Boot 3.2.x
- Spring Data JPA + Hibernate
- PostgreSQL 16
- Flyway migrations
- Swagger/OpenAPI
- React frontend rendered from browser ES modules under `src/main/resources/static/react-app`

## Project Structure

- Backend code: `src/main/java/com/vymo/bandviz`
- DB migrations: `src/main/resources/db/migration`
- React app entry: `src/main/resources/static/react-app`
- Spring-served page wrappers: `src/main/resources/static`

## Prerequisites

1. Java 25+ (`java -version` should show 25 or newer)
2. Maven 3.9+
3. PostgreSQL running locally

## Environment Variables

The app now auto-loads variables from a root `.env` file (recommended).
You can start from `.env.example`:

```bash
cp .env.example .env
```

Equivalent manual exports:

```bash
export DB_USERNAME=bandviz
export DB_PASSWORD=bandviz

export JIRA_BASE_URL="https://your-org.atlassian.net"
export JIRA_EMAIL="your-email@company.com"
export JIRA_API_TOKEN="your-jira-api-token"
export JIRA_SYNC_ENABLED=false
export JIRA_PROJECT_KEYS="CRM,ANA,MOB"
```

Notes:
- Jira is optional for local usage.
- Keep `JIRA_SYNC_ENABLED=false` if you do not want external Jira calls.
- `JIRA_PROJECT_KEYS` is optional. If set, only those Jira project keys are synced.
- If `JIRA_PROJECT_KEYS` is not set, the app falls back to active projects from the database that have `jiraProjectKey` populated.

## PostgreSQL Setup

Create DB and user (example):

```sql
CREATE DATABASE bandviz;
CREATE USER bandviz WITH PASSWORD 'bandviz';
GRANT ALL PRIVILEGES ON DATABASE bandviz TO bandviz;
```

`application.yml` expects:
- DB URL: `jdbc:postgresql://localhost:5432/bandviz`
- Username/password from env vars above

### Jira Project Selection

You can explicitly control which Jira projects are synced with:

```bash
export JIRA_PROJECT_KEYS="CRM,ANA,MOB"
```

This maps to:

```yaml
bandviz:
  jira:
    project-keys: ${JIRA_PROJECT_KEYS:}
```

Behavior:
- If `bandviz.jira.project-keys` is configured, only those project keys are synced.
- If it is empty, BandViz syncs all active projects in the database with a non-empty `jiraProjectKey`.

## Build and Run

From project root:

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

`application.yml` imports `optional:file:.env[.properties]`, so `.env` is picked up automatically when running from project root.

Frontend note:
- This repo now serves a React frontend directly from browser ES modules.
- In this workspace, `node`/`npm` are not installed, so the React app does not use a local bundler yet.
- React, ReactDOM, HTM, and Scheduler are vendored locally under `src/main/resources/static/react-app/vendor`.

On startup:
- Flyway applies `V1` to latest migration (`V15` currently)
- The schema is created without demo seed data

## Jira Seed Script

For repeatable Jira-based seeding, use:

```bash
./scripts/seed_from_jira.sh seed-global
```

Notes:
- `scripts/seed_from_jira.sh` auto-loads `.env` from project root if present.
- You can still override values by exporting env vars in your shell.

Supported commands:
- `seed-global` (also supports legacy alias: `seed-all`)
- `seed-teams`
- `seed-projects`
- `seed-developers`

Required env vars:
- `JIRA_BASE_URL`
- `JIRA_EMAIL`
- `JIRA_API_TOKEN`
- `ATLASSIAN_ORG_ID`
- `ATLASSIAN_SITE_ID`

Optional env vars:
- `ATLASSIAN_TEAM_NAMES` comma-separated Atlassian team display names for developer seeding
- `ATLASSIAN_TEAM_IDS` comma-separated fallback team IDs for developer seeding
- `ATLASSIAN_TEAM_PROJECT_KEYS_JSON` JSON object mapping Atlassian team name or team id to Jira project keys
- `BANDVIZ_BASE_URL` default: `http://localhost:8080`
- `BANDVIZ_DEFAULT_DEVELOPER_ROLE` default: `DEVELOPER`
- `BANDVIZ_DEFAULT_WEEKLY_CAPACITY` default: `40`
- `BANDVIZ_DEFAULT_TARGET_UTILIZATION` default: `70`
- `BANDVIZ_DEFAULT_DELIVERY_MODE` default: `HYBRID`
- `ATLASSIAN_DUPLICATE_USER_TEAM_STRATEGY` values: `first` (default) or `error`
- `DRY_RUN=true` to print summary without creating or updating records

What it does:
- Atlassian teams are fetched and upserted into BandViz `/api/teams`
- Jira projects are fetched from `/rest/api/3/project/search` and upserted into BandViz by `jiraProjectKey` with mapped `teamId` (primary) and `permittedTeamIds`
- Jira developers are fetched from selected Atlassian teams, resolved through `/rest/api/3/user`, and upserted into BandViz by email with `teamId`
- Existing BandViz records are updated if the Jira-derived values changed

Example global seed config:

```bash
export ATLASSIAN_ORG_ID="your-org-id"
export ATLASSIAN_SITE_ID="your-site-id"
export ATLASSIAN_TEAM_NAMES="Collections BAU,Prod-Eng"
export ATLASSIAN_TEAM_PROJECT_KEYS_JSON='{
  "Collections BAU": ["COLL", "COLLTKT"],
  "Prod-Eng": ["PE", "OPS"]
}'

./scripts/seed_from_jira.sh seed-global
```

## Access URLs

- Home: `http://localhost:8080/home`
- Team Dashboard: `http://localhost:8080/team-dashboard`
- Developer Detail: `http://localhost:8080/developer-detail`
- Leave Calendar: `http://localhost:8080/leave-calendar`
- Bandwidth Planner: `http://localhost:8080/capacity-planner`
- Jira Sync: `http://localhost:8080/jira-sync`
- Settings: `http://localhost:8080/settings`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

Legacy `.html` page URLs still work and forward-compatible links now use the cleaner route-style endpoints above.

## Core APIs

### Developers
- `GET /api/developers`
- `GET /api/developers?teamId={id}`
- `GET /api/developers/{id}`
- `POST /api/developers`
- `PUT /api/developers/{id}`
- `DELETE /api/developers/{id}`

Developer payloads also support:
- `teamId`

### Projects
- `GET /api/projects`
- `GET /api/projects?teamId={id}`
- `GET /api/projects/{id}`
- `POST /api/projects`
- `PUT /api/projects/{id}`
- `DELETE /api/projects/{id}`

Project payloads also support:
- `deliveryMode` with values `SPRINT`, `KANBAN`, `HYBRID`
- `teamId`
- `permittedTeamIds` (optional list; `teamId` is always included automatically)

### Teams
- `GET /api/teams`
- `GET /api/teams/{id}`
- `POST /api/teams`
- `PUT /api/teams/{id}`
- `DELETE /api/teams/{id}`

Team payloads:
- `name`
- `description`

Example flow for Collections:
1. Create a team like `Collections BAU`
2. Assign a project primary owner with `teamId`
3. Add additional allowed teams with `permittedTeamIds` where needed
4. Tag developers to their Jira-linked team with `teamId`
5. Review synced Jira tickets through the same ownership chain

### Project Allocations
- `GET /api/project-allocations?developerId={id}`
- `GET /api/project-allocations?projectId={id}`
- `POST /api/project-allocations`
- `PUT /api/project-allocations/{id}`
- `DELETE /api/project-allocations/{id}`

### Leave Requests
- `GET /api/leave-requests?developerId={id}`
- `GET /api/leave-requests/pending-approvals`
- `POST /api/leave-requests`
- `POST /api/leave-requests/{id}/approve`
- `POST /api/leave-requests/{id}/reject`
- `DELETE /api/leave-requests/{id}`

### Sprints
- `GET /api/sprints`
- `GET /api/sprints/current`
- `POST /api/sprints`
- `PUT /api/sprints/{id}`

### Capacity and Dashboard Summary
- `GET /api/capacity`
- `GET /api/capacity?sprintId={id}`
- `GET /api/capacity?start=YYYY-MM-DD&end=YYYY-MM-DD`
- `GET /api/dashboard-summary`
- `GET /api/dashboard-summary?sprintId={id}`

### Jira Tickets
- `GET /api/jira-tickets?developerId={id}`
- `GET /api/jira-tickets?projectId={id}`
- `GET /api/jira-tickets?teamId={id}`

Behavior:
- If there is an active sprint, dashboard and capacity use that sprint window
- If there is no active sprint, BandViz falls back to a rolling planning window
- Default fallback window length is `14` days and can be changed with `bandviz.planning.default-window-days`

### Jira Sync
- `POST /api/jira-sync/runs`
- `GET /api/jira-sync/status`

## Testing

```bash
./mvnw test
```

`application-test.yml` uses H2 in-memory DB and disables Flyway/Jira sync.

## Troubleshooting

### `invalid flag: --release`
You are using an unsupported Java version for this build. Use Java 25+ (`java -version` should show 25 or newer).

### Flyway migration errors
- Ensure DB is reachable and credentials are correct.
- Ensure DB user has schema create and alter rights.

### Jira sync failures
- Verify `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`.
- Disable sync locally with `JIRA_SYNC_ENABLED=false`.
- Jira Cloud search now uses the enhanced search API (`/rest/api/3/search/jql`) rather than the deprecated `/rest/api/3/search`.

## Current Status

- Backend API contracts from LLD are implemented.
- Frontend pages are wired to live APIs.
- The application starts with an empty schema unless you add your own data.
