# BandViz

BandViz is a web-based post-sale bandwidth management application for engineering managers.

It provides:
- Team bandwidth and utilization view
- Developer-level allocation and Jira ticket load
- Leave approval workflow and calendar
- Bandwidth planner for project allocations
- Jira sync status and on-demand sync

BandViz now supports mixed delivery models:
- Sprint teams can plan against an active sprint
- Kanban teams can plan against a rolling planning window
- Projects can be marked as `SPRINT`, `KANBAN`, or `HYBRID`

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

1. Java 21+ (`java -version` should show 21 or newer)
2. Maven 3.9+
3. PostgreSQL running locally

## Environment Variables

Set these before running:

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

Frontend note:
- This repo now serves a React frontend directly from browser ES modules.
- In this workspace, `node`/`npm` are not installed, so the React app does not use a local bundler yet.
- React, ReactDOM, HTM, and Scheduler are vendored locally under `src/main/resources/static/react-app/vendor`.

On startup:
- Flyway applies `V1` to `V9` migrations
- The schema is created without demo seed data

## Jira Seed Script

For repeatable Jira-based seeding, use:

```bash
source ~/.zshrc
./scripts/seed_from_jira.sh seed-all
```

Supported commands:
- `seed-all`
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
- `BANDVIZ_BASE_URL` default: `http://localhost:8080`
- `BANDVIZ_DEFAULT_DEVELOPER_ROLE` default: `DEVELOPER`
- `BANDVIZ_DEFAULT_WEEKLY_CAPACITY` default: `40`
- `BANDVIZ_DEFAULT_TARGET_UTILIZATION` default: `70`
- `DRY_RUN=true` to print summary without creating or updating records

What it does:
- Jira projects are fetched from `/rest/api/3/project/search` and upserted into BandViz by `jiraProjectKey`
- Jira developers are fetched from one or more Atlassian team names or team IDs, resolved through `/rest/api/3/user`, and upserted into BandViz by email
- Existing BandViz records are updated if the Jira-derived values changed

Example developer seed config:

```bash
export ATLASSIAN_ORG_ID="your-org-id"
export ATLASSIAN_SITE_ID="your-site-id"
export ATLASSIAN_TEAM_NAMES="Collections BAU,Prod-Eng"
```

## Access URLs

- Home: `http://localhost:8080/home.html`
- Team Dashboard: `http://localhost:8080/team-dashboard.html`
- Developer Detail: `http://localhost:8080/developer-detail.html`
- Leave Calendar: `http://localhost:8080/leave-calendar.html`
- Bandwidth Planner: `http://localhost:8080/capacity-planner.html`
- Jira Sync: `http://localhost:8080/jira-sync.html`
- Settings: `http://localhost:8080/settings.html`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Core APIs

### Developers
- `GET /api/developers`
- `GET /api/developers/{id}`
- `POST /api/developers`
- `PUT /api/developers/{id}`
- `DELETE /api/developers/{id}`

### Projects
- `GET /api/projects`
- `GET /api/projects/{id}`
- `POST /api/projects`
- `PUT /api/projects/{id}`
- `DELETE /api/projects/{id}`

Project payloads also support:
- `deliveryMode` with values `SPRINT`, `KANBAN`, `HYBRID`

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

Behavior:
- If there is an active sprint, dashboard and capacity use that sprint window
- If there is no active sprint, BandViz falls back to a rolling planning window
- Default fallback window length is `14` days and can be changed with `bandviz.planning.default-window-days`

### Jira Sync
- `POST /api/jira-sync/runs`
- `GET /api/jira-sync/status`

Legacy route aliases remain available for the older paths under `/api/assignments`, `/api/leaves`, `/api/bandwidth`, `/api/dashboard`, `/api/jira`, and `/api/sprints/active`.

## Testing

```bash
./mvnw test
```

`application-test.yml` uses H2 in-memory DB and disables Flyway/Jira sync.

## Troubleshooting

### `invalid flag: --release`
You are using an unsupported Java version for this build. Use Java 21 or newer, but avoid compiling this project as Java 25 unless you also upgrade the Spring Boot plugin/toolchain.

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
