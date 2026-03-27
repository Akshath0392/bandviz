# BandViz — Low-Level Design Document

**Version:** 1.0
**Date:** 2026-03-25
**Author:** Post-Sales Engineering
**Status:** Draft

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Package Structure](#3-package-structure)
4. [Database Design](#4-database-design)
5. [Entity Relationship Diagram](#5-entity-relationship-diagram)
6. [Domain Models](#6-domain-models)
7. [API Contracts](#7-api-contracts)
8. [Service Layer Design](#8-service-layer-design)
9. [Bandwidth Calculation Algorithm](#9-bandwidth-calculation-algorithm)
10. [Jira Integration](#10-jira-integration)
11. [Key Sequence Diagrams](#11-key-sequence-diagrams)
12. [Error Handling](#12-error-handling)
13. [Configuration](#13-configuration)
14. [Security Considerations](#14-security-considerations)
15. [Future Enhancements](#15-future-enhancements)

---

## 1. Overview

### 1.1 Purpose
BandViz is an internal bandwidth management tool for a 17-developer post-sales engineering team. It provides a unified view of:
- Developer bandwidth and allocation across projects
- Jira ticket load per developer
- Leave status and calendar

### 1.2 Goals
- **Manager-only tool** (v1): Engineering manager views and manages allocations
- **Single source of truth** for "who is working on what and how loaded are they"
- **Jira as a signal, not the source**: Manual allocation is authoritative; Jira provides ticket-level visibility

### 1.3 Tech Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Language      | Java 21                             |
| Framework     | Spring Boot 3.2.x                   |
| ORM           | Spring Data JPA + Hibernate 6       |
| Database      | PostgreSQL 16                       |
| Migrations    | Flyway                              |
| API Docs      | Springdoc OpenAPI 2.x (Swagger UI)  |
| Jira Client   | RestTemplate + Jira REST API v3     |
| Build         | Maven                               |
| Frontend      | React (separate repo, out of scope) |

### 1.4 Non-Goals (v1)
- Authentication / authorization (no JWT, no roles)
- Developer self-service (leave apply, view own dashboard)
- Webhooks from Jira (pull-only model)
- Email / Slack notifications
- Audit trail

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌───────────────────────────────────────────────────────┐
│                  React Frontend                        │
│          (Separate repo, consumes REST API)             │
└──────────────────────┬────────────────────────────────┘
                       │  HTTP / JSON
                       ▼
┌──────────────────────────────────────────────────────────┐
│                  Spring Boot Application                  │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Controllers  │  │   Services   │  │  Integration  │  │
│  │  (REST API)   │──│  (Business   │──│  (Jira Client)│  │
│  │              │  │   Logic)     │  │               │  │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘  │
│         │                 │                   │          │
│  ┌──────▼─────────────────▼───────┐   ┌──────▼───────┐  │
│  │   Spring Data JPA Repositories │   │  Jira Cloud  │  │
│  └──────────────┬─────────────────┘   │  REST API    │  │
│                 │                     └──────────────┘  │
└─────────────────┼───────────────────────────────────────┘
                  │  JDBC
                  ▼
         ┌────────────────┐
         │  PostgreSQL 16  │
         │   (bandviz DB)  │
         └────────────────┘
```

### 2.2 Layer Responsibilities

| Layer          | Responsibility                                                  |
|----------------|-----------------------------------------------------------------|
| **Controller** | HTTP handling, request validation, response shaping              |
| **DTO**        | Request/Response objects — entities never leak to API            |
| **Service**    | Business logic, orchestration, bandwidth calculation             |
| **Repository** | Data access, custom JPQL queries                                |
| **Domain**     | JPA entities, enums — pure data model                           |
| **Integration**| External API clients (Jira), isolated from core business logic  |
| **Exception**  | Global error handler, custom exception types                    |
| **Config**     | Spring configs, CORS, external property binding                 |

### 2.3 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Bandwidth source | Manual allocation (Assignment table) | Manager explicitly sets % — more accurate than Jira-derived estimates |
| Jira role | Read-only signal layer | Provides ticket counts, story points, status for cross-checking bandwidth |
| Sprint model | Stored locally in DB | Decoupled from Jira sprints; manager defines sprint boundaries |
| Leave approval | Single-step (manager approves) | No roles in v1; manager is the only user |
| Soft delete | `active` boolean flag | Developers and projects deactivated, not deleted (preserves history) |
| Pagination | Not in v1 (17 developers) | Team is small; will add if team grows past 50 |

---

## 3. Package Structure

```
com.vymo.bandviz/
│
├── BandVizApplication.java            # Spring Boot entry point
│
├── config/
│   ├── CorsConfig.java                # CORS policy for frontend
│   ├── JiraProperties.java            # @ConfigurationProperties for Jira
│   └── RestTemplateConfig.java        # RestTemplate bean with timeouts
│
├── domain/
│   ├── Developer.java                 # @Entity
│   ├── Project.java                   # @Entity
│   ├── Assignment.java                # @Entity (dev ↔ project allocation)
│   ├── Leave.java                     # @Entity
│   ├── Sprint.java                    # @Entity
│   ├── JiraTicket.java                # @Entity (synced from Jira)
│   └── enums/
│       ├── DeveloperRole.java
│       ├── LeaveType.java
│       ├── LeaveStatus.java
│       ├── BandwidthStatus.java
│       ├── TicketStatus.java
│       └── TicketPriority.java
│
├── repository/
│   ├── DeveloperRepository.java
│   ├── ProjectRepository.java
│   ├── AssignmentRepository.java
│   ├── LeaveRepository.java
│   ├── SprintRepository.java
│   └── JiraTicketRepository.java
│
├── dto/
│   ├── request/
│   │   ├── DeveloperRequest.java
│   │   ├── ProjectRequest.java
│   │   ├── AssignmentRequest.java
│   │   ├── LeaveRequest.java
│   │   └── SprintRequest.java
│   └── response/
│       ├── DeveloperResponse.java
│       ├── ProjectResponse.java
│       ├── AssignmentResponse.java
│       ├── LeaveResponse.java
│       ├── SprintResponse.java
│       ├── DeveloperBandwidthResponse.java
│       └── DashboardResponse.java
│
├── service/
│   ├── DeveloperService.java
│   ├── ProjectService.java
│   ├── AssignmentService.java
│   ├── LeaveService.java
│   ├── BandwidthService.java          # Core bandwidth engine
│   ├── JiraSyncService.java           # Scheduled Jira pull
│   └── DashboardService.java          # Aggregation layer
│
├── controller/
│   ├── DeveloperController.java
│   ├── ProjectController.java
│   ├── AssignmentController.java
│   ├── LeaveController.java
│   ├── SprintController.java
│   ├── BandwidthController.java
│   ├── JiraSyncController.java
│   └── DashboardController.java
│
├── integration/
│   ├── JiraClient.java                # Jira REST API wrapper
│   └── dto/
│       ├── JiraIssueResponse.java     # Deserialization of Jira issue JSON
│       └── JiraSearchResponse.java    # Deserialization of Jira search JSON
│
└── exception/
    ├── ResourceNotFoundException.java  # 404
    ├── BusinessException.java          # 422 (validation/business rule)
    └── GlobalExceptionHandler.java     # @ControllerAdvice
```

---

## 4. Database Design

### 4.1 Tables

#### `developers`
| Column                | Type         | Constraints               | Notes                   |
|-----------------------|--------------|---------------------------|-------------------------|
| `id`                  | BIGSERIAL    | PK                        |                         |
| `name`                | VARCHAR(100) | NOT NULL                  |                         |
| `email`               | VARCHAR(150) | NOT NULL, UNIQUE          |                         |
| `role`                | VARCHAR(50)  | NOT NULL                  | Enum: DeveloperRole     |
| `weekly_capacity_hours`| INTEGER     | NOT NULL, DEFAULT 40      | 1–80 range              |
| `jira_username`       | VARCHAR(100) | UNIQUE                    | Jira email / accountId  |
| `active`              | BOOLEAN      | NOT NULL, DEFAULT true    | Soft delete flag        |

#### `projects`
| Column                  | Type         | Constraints               | Notes                 |
|-------------------------|--------------|---------------------------|-----------------------|
| `id`                    | BIGSERIAL    | PK                        |                       |
| `name`                  | VARCHAR(100) | NOT NULL, UNIQUE          |                       |
| `jira_project_key`      | VARCHAR(20)  | UNIQUE                    | e.g., "VYMO"         |
| `color`                 | VARCHAR(10)  |                           | Hex code for UI       |
| `target_utilization_pct`| INTEGER      | NOT NULL, DEFAULT 70      | 0–100                 |
| `active`                | BOOLEAN      | NOT NULL, DEFAULT true    |                       |

#### `assignments`
| Column          | Type       | Constraints                          | Notes                        |
|-----------------|------------|--------------------------------------|------------------------------|
| `id`            | BIGSERIAL  | PK                                   |                              |
| `developer_id`  | BIGINT     | NOT NULL, FK → developers(id)        |                              |
| `project_id`    | BIGINT     | NOT NULL, FK → projects(id)          |                              |
| `allocation_pct`| INTEGER    | NOT NULL                             | 0–100, can sum >100 (alert)  |
| `start_date`    | DATE       | NOT NULL                             |                              |
| `end_date`      | DATE       |                                      | NULL = open-ended / ongoing  |

**Unique constraint:** `(developer_id, project_id, start_date)` — prevents duplicate allocation records.

**Index:** `idx_assignments_developer_dates ON (developer_id, start_date, end_date)` — used by the bandwidth query.

#### `leaves`
| Column          | Type       | Constraints                        | Notes                   |
|-----------------|------------|------------------------------------|-------------------------|
| `id`            | BIGSERIAL  | PK                                 |                         |
| `developer_id`  | BIGINT     | NOT NULL, FK → developers(id)      |                         |
| `leave_type`    | VARCHAR(30)| NOT NULL                           | Enum: LeaveType         |
| `start_date`    | DATE       | NOT NULL                           |                         |
| `end_date`      | DATE       | NOT NULL                           |                         |
| `status`        | VARCHAR(20)| NOT NULL, DEFAULT 'PENDING'        | Enum: LeaveStatus       |
| `notes`         | TEXT       |                                    | Optional reason         |

**Index:** `idx_leaves_dev_dates_status ON (developer_id, start_date, end_date, status)` — used by bandwidth calculation.

#### `sprints`
| Column      | Type         | Constraints              | Notes                      |
|-------------|--------------|--------------------------|----------------------------|
| `id`        | BIGSERIAL    | PK                       |                            |
| `name`      | VARCHAR(50)  | NOT NULL, UNIQUE         | e.g., "Sprint 42"         |
| `start_date`| DATE         | NOT NULL                 |                            |
| `end_date`  | DATE         | NOT NULL                 |                            |
| `active`    | BOOLEAN      | NOT NULL, DEFAULT false  | Only one active at a time  |

**Business rule:** At most one sprint has `active = true`. Enforced at service layer, not DB constraint.

#### `jira_tickets`
| Column                  | Type          | Constraints         | Notes                        |
|-------------------------|---------------|---------------------|------------------------------|
| `id`                    | BIGSERIAL     | PK                  |                              |
| `ticket_key`            | VARCHAR(30)   | NOT NULL, UNIQUE    | e.g., "VYMO-431"            |
| `summary`               | TEXT          |                     |                              |
| `assignee_jira_username`| VARCHAR(100)  |                     | Matched to Developer.jiraUsername |
| `story_points`          | INTEGER       |                     | Nullable (not all tickets have SP) |
| `status`                | VARCHAR(30)   |                     | Enum: TicketStatus           |
| `priority`              | VARCHAR(20)   |                     | Enum: TicketPriority         |
| `project_key`           | VARCHAR(20)   |                     | Jira project key             |
| `sprint_name`           | VARCHAR(100)  |                     | Active sprint at sync time   |
| `last_synced_at`        | TIMESTAMP     |                     |                              |

**Index:** `idx_jira_tickets_assignee ON (assignee_jira_username)` — developer detail lookups.
**Index:** `idx_jira_tickets_project ON (project_key)` — project breakdown queries.

---

## 5. Entity Relationship Diagram

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  developers  │       │  assignments │       │   projects   │
├──────────────┤       ├──────────────┤       ├──────────────┤
│ PK id        │──┐    │ PK id        │    ┌──│ PK id        │
│ name         │  │    │ FK developer │────┘  │ name         │
│ email        │  └───▶│ FK project   │───────│ jira_project │
│ role         │       │ allocation%  │       │ color        │
│ capacity_hrs │       │ start_date   │       │ target_pct   │
│ jira_username│       │ end_date     │       │ active       │
│ active       │       └──────────────┘       └──────────────┘
└──────┬───────┘
       │
       │ 1:N
       ▼
┌──────────────┐                              ┌──────────────┐
│    leaves    │                              │   sprints    │
├──────────────┤                              ├──────────────┤
│ PK id        │                              │ PK id        │
│ FK developer │                              │ name         │
│ leave_type   │                              │ start_date   │
│ start_date   │                              │ end_date     │
│ end_date     │                              │ active       │
│ status       │                              └──────────────┘
│ notes        │
└──────────────┘

┌─────────────────┐
│  jira_tickets   │
├─────────────────┤     Linked to Developer via
│ PK id           │     jira_username (soft FK,
│ ticket_key      │     not a DB foreign key)
│ summary         │
│ assignee_jira   │─ ─ ─ ─ ─ ─ ─ ─ ┐
│ story_points    │                 │
│ status          │          developers.jira_username
│ priority        │
│ project_key     │─ ─ ─ ─ ─ ─ ─ ─ ┐
│ sprint_name     │                 │
│ last_synced_at  │          projects.jira_project_key
└─────────────────┘
```

**Relationship summary:**
- `Developer 1 : N Assignment` — a developer can be on multiple projects
- `Project 1 : N Assignment` — a project has multiple developers
- `Developer 1 : N Leave` — a developer can have many leave records
- `JiraTicket → Developer` — soft link via `assignee_jira_username = Developer.jiraUsername`
- `JiraTicket → Project` — soft link via `project_key = Project.jiraProjectKey`
- `Sprint` — standalone; referenced by date range in bandwidth queries

---

## 6. Domain Models

### 6.1 Enums

```
DeveloperRole:
  SENIOR_BACKEND_ENGINEER | BACKEND_ENGINEER | SENIOR_FRONTEND_ENGINEER
  FRONTEND_ENGINEER | FULL_STACK_ENGINEER | QA_ENGINEER | DEVOPS_ENGINEER

LeaveType:
  PLANNED | SICK | COMP_OFF | UNPAID | PUBLIC_HOLIDAY

LeaveStatus:
  PENDING | APPROVED | REJECTED

BandwidthStatus:
  AVAILABLE | BUSY | OVERLOADED | ON_LEAVE

TicketStatus:
  TO_DO | IN_PROGRESS | IN_REVIEW | BLOCKED | DONE

TicketPriority:
  HIGHEST | HIGH | MEDIUM | LOW | LOWEST
```

### 6.2 State Transitions

**LeaveStatus:**
```
  ┌─────────┐
  │ PENDING │
  └────┬────┘
       │
   ┌───┴────┐
   ▼        ▼
┌──────┐ ┌──────────┐
│APPROV│ │ REJECTED │
└──────┘ └──────────┘
```
- Only `PENDING` → `APPROVED` or `PENDING` → `REJECTED` transitions are allowed.
- Approved leaves cannot be deleted directly — must be rejected first.

---

## 7. API Contracts

### 7.1 Developers

| Method   | Endpoint              | Request Body       | Response           | Status |
|----------|-----------------------|--------------------|--------------------|--------|
| `GET`    | `/api/developers`     | —                  | `DeveloperResponse[]` | 200 |
| `GET`    | `/api/developers/{id}`| —                  | `DeveloperResponse`   | 200 |
| `POST`   | `/api/developers`     | `DeveloperRequest` | `DeveloperResponse`   | 201 |
| `PUT`    | `/api/developers/{id}`| `DeveloperRequest` | `DeveloperResponse`   | 200 |
| `DELETE` | `/api/developers/{id}`| —                  | —                     | 204 |

**Query params for GET list:**
- `activeOnly` (boolean, default `true`) — filter active developers only

**DeveloperRequest:**
```json
{
  "name": "Arjun Kumar",
  "email": "arjun.k@vymo.com",
  "role": "SENIOR_BACKEND_ENGINEER",
  "weeklyCapacityHours": 40,
  "jiraUsername": "arjun.k@vymo.com"
}
```

**DeveloperResponse:**
```json
{
  "id": 1,
  "name": "Arjun Kumar",
  "email": "arjun.k@vymo.com",
  "role": "SENIOR_BACKEND_ENGINEER",
  "weeklyCapacityHours": 40,
  "jiraUsername": "arjun.k@vymo.com",
  "active": true
}
```

### 7.2 Projects

| Method   | Endpoint              | Request Body       | Response              | Status |
|----------|-----------------------|--------------------|-----------------------|--------|
| `GET`    | `/api/projects`       | —                  | `ProjectResponse[]`   | 200 |
| `GET`    | `/api/projects/{id}`  | —                  | `ProjectResponse`     | 200 |
| `POST`   | `/api/projects`       | `ProjectRequest`   | `ProjectResponse`     | 201 |
| `PUT`    | `/api/projects/{id}`  | `ProjectRequest`   | `ProjectResponse`     | 200 |
| `DELETE` | `/api/projects/{id}`  | —                  | —                     | 204 |

**ProjectRequest:**
```json
{
  "name": "CRM Core",
  "jiraProjectKey": "VYMO",
  "color": "#6366f1",
  "targetUtilizationPct": 70
}
```

### 7.3 Assignments

| Method   | Endpoint                         | Request Body         | Response                | Status |
|----------|----------------------------------|----------------------|-------------------------|--------|
| `GET`    | `/api/assignments?developerId=1` | —                    | `AssignmentResponse[]`  | 200 |
| `GET`    | `/api/assignments?projectId=2`   | —                    | `AssignmentResponse[]`  | 200 |
| `POST`   | `/api/assignments`               | `AssignmentRequest`  | `AssignmentResponse`    | 201 |
| `PUT`    | `/api/assignments/{id}`          | `AssignmentRequest`  | `AssignmentResponse`    | 200 |
| `DELETE` | `/api/assignments/{id}`          | —                    | —                       | 204 |

**AssignmentRequest:**
```json
{
  "developerId": 1,
  "projectId": 2,
  "allocationPct": 50,
  "startDate": "2026-03-17",
  "endDate": null
}
```

**AssignmentResponse:**
```json
{
  "id": 1,
  "developerId": 1,
  "developerName": "Arjun Kumar",
  "projectId": 2,
  "projectName": "Analytics 2.0",
  "allocationPct": 50,
  "startDate": "2026-03-17",
  "endDate": null
}
```

### 7.4 Leaves

| Method   | Endpoint                | Request Body   | Response            | Status |
|----------|-------------------------|----------------|---------------------|--------|
| `GET`    | `/api/leaves?developerId=1` | —          | `LeaveResponse[]`   | 200 |
| `GET`    | `/api/leaves/pending`   | —              | `LeaveResponse[]`   | 200 |
| `POST`   | `/api/leaves`           | `LeaveRequest` | `LeaveResponse`     | 201 |
| `POST`   | `/api/leaves/{id}/approve` | —           | `LeaveResponse`     | 200 |
| `POST`   | `/api/leaves/{id}/reject`  | —           | `LeaveResponse`     | 200 |
| `DELETE` | `/api/leaves/{id}`      | —              | —                   | 204 |

**Query params for GET list:**
- `developerId` (Long) — filter by developer
- `startDate`, `endDate` (LocalDate) — filter by date range

**LeaveRequest:**
```json
{
  "developerId": 3,
  "leaveType": "PLANNED",
  "startDate": "2026-03-24",
  "endDate": "2026-03-26",
  "notes": "Family vacation"
}
```

**LeaveResponse:**
```json
{
  "id": 5,
  "developerId": 3,
  "developerName": "Siddharth M.",
  "leaveType": "PLANNED",
  "startDate": "2026-03-24",
  "endDate": "2026-03-26",
  "status": "APPROVED",
  "notes": "Family vacation",
  "durationDays": 3
}
```

### 7.5 Sprints

| Method   | Endpoint              | Request Body     | Response            | Status |
|----------|-----------------------|------------------|---------------------|--------|
| `GET`    | `/api/sprints`        | —                | `SprintResponse[]`  | 200 |
| `GET`    | `/api/sprints/active` | —                | `SprintResponse`    | 200 |
| `POST`   | `/api/sprints`        | `SprintRequest`  | `SprintResponse`    | 201 |
| `PUT`    | `/api/sprints/{id}`   | `SprintRequest`  | `SprintResponse`    | 200 |

### 7.6 Bandwidth

| Method | Endpoint                         | Response                        | Status |
|--------|----------------------------------|---------------------------------|--------|
| `GET`  | `/api/bandwidth`                 | `DeveloperBandwidthResponse[]`  | 200 |
| `GET`  | `/api/bandwidth?sprintId=5`      | `DeveloperBandwidthResponse[]`  | 200 |
| `GET`  | `/api/bandwidth?start=...&end=...` | `DeveloperBandwidthResponse[]` | 200 |

**DeveloperBandwidthResponse:**
```json
{
  "developerId": 1,
  "developerName": "Arjun Kumar",
  "role": "SENIOR_BACKEND_ENGINEER",
  "jiraUsername": "arjun.k@vymo.com",
  "weeklyCapacityHours": 40,
  "totalAllocationPct": 95,
  "effectiveBandwidthPct": 95.0,
  "status": "OVERLOADED",
  "leaveDaysInPeriod": 0,
  "workingDaysInPeriod": 10,
  "openTickets": 8,
  "blockedTickets": 1,
  "totalStoryPoints": 34,
  "projectAllocations": [
    {
      "projectId": 1,
      "projectName": "CRM Core",
      "projectColor": "#6366f1",
      "allocationPct": 50
    },
    {
      "projectId": 2,
      "projectName": "Analytics 2.0",
      "projectColor": "#a855f7",
      "allocationPct": 35
    }
  ]
}
```

### 7.7 Dashboard

| Method | Endpoint                        | Response            | Status |
|--------|---------------------------------|---------------------|--------|
| `GET`  | `/api/dashboard`                | `DashboardResponse` | 200 |
| `GET`  | `/api/dashboard?sprintId=5`     | `DashboardResponse` | 200 |

**DashboardResponse:**
```json
{
  "sprintName": "Sprint 42",
  "sprintStart": "2026-03-17",
  "sprintEnd": "2026-03-28",
  "totalDevelopers": 17,
  "availableCount": 9,
  "busyCount": 5,
  "overloadedCount": 3,
  "onLeaveCount": 4,
  "averageUtilizationPct": 68.0,
  "totalOpenTickets": 84,
  "totalBlockedTickets": 7,
  "totalClosedThisSprint": 51,
  "alerts": [
    { "severity": "HIGH", "message": "3 developer(s) are overloaded..." },
    { "severity": "HIGH", "message": "Arjun Kumar has 1 blocked ticket(s)." }
  ],
  "developers": [ ... ]
}
```

### 7.8 Jira Sync

| Method | Endpoint            | Response              | Status |
|--------|---------------------|-----------------------|--------|
| `POST` | `/api/jira/sync`    | `SyncResult`          | 200 |
| `GET`  | `/api/jira/status`  | `SyncStatusResponse`  | 200 |

**SyncResult:**
```json
{
  "ticketsSynced": 84,
  "errors": 0,
  "message": "OK · 84 tickets synced · 0 project errors"
}
```

**SyncStatusResponse:**
```json
{
  "syncEnabled": true,
  "lastSyncedAt": "2026-03-25T10:30:00",
  "lastSyncStatus": "OK · 84 tickets synced · 0 project errors",
  "totalTickets": 135
}
```

---

## 8. Service Layer Design

### 8.1 Service Dependency Graph

```
DashboardController ──▶ DashboardService
                              │
                              ├──▶ BandwidthService (core calculation)
                              │       │
                              │       ├──▶ DeveloperRepository
                              │       ├──▶ AssignmentRepository
                              │       ├──▶ LeaveService (leave day counting)
                              │       ├──▶ JiraTicketRepository
                              │       └──▶ SprintRepository
                              │
                              ├──▶ SprintRepository
                              └──▶ JiraTicketRepository

LeaveController ──▶ LeaveService
                       └──▶ LeaveRepository
                       └──▶ DeveloperService (validation)

AssignmentController ──▶ AssignmentService
                            ├──▶ AssignmentRepository
                            ├──▶ DeveloperService (validation)
                            └──▶ ProjectService (validation)

JiraSyncController ──▶ JiraSyncService
                          ├──▶ JiraClient (HTTP calls to Jira)
                          ├──▶ JiraTicketRepository
                          └──▶ ProjectRepository
```

### 8.2 Service Responsibilities

| Service             | Methods                                                       | Notes |
|---------------------|---------------------------------------------------------------|-------|
| `DeveloperService`  | findAll, findById, create, update, deactivate                 | CRUD + soft delete |
| `ProjectService`    | findAll, findById, create, update, deactivate                 | CRUD + soft delete |
| `AssignmentService` | findByDeveloper, findByProject, create, update, delete        | Hard delete (historical assignments can be recreated) |
| `LeaveService`      | findByDev, findPending, apply, approve, reject, delete, countApprovedLeaveDays, countWorkingDays | Core leave logic + utility methods |
| `BandwidthService`  | computeForPeriod, computeForActiveSprint, computeForSprint    | Core bandwidth engine |
| `DashboardService`  | getActiveSprint, getForSprint                                 | Aggregation + alerts |
| `JiraSyncService`   | syncAll (scheduled), getStatus                                | Jira pull + upsert |

---

## 9. Bandwidth Calculation Algorithm

This is the core business logic of the application.

### 9.1 Formula

```
For a given developer `d` in period [startDate, endDate]:

  workingDays = count of Mon–Fri days in [startDate, endDate]

  leaveDays = count of Mon–Fri days covered by APPROVED leaves
              that overlap with [startDate, endDate]

  leaveFraction = leaveDays / workingDays       (0.0 to 1.0)

  totalAllocationPct = SUM(a.allocationPct)
                       for all active assignments `a`
                       where a.startDate <= midpointDate
                       AND (a.endDate IS NULL OR a.endDate >= midpointDate)

  effectiveBandwidthPct = totalAllocationPct × (1 - leaveFraction)
```

### 9.2 Status Resolution

```
  if leaveDays >= workingDays      → ON_LEAVE
  if effectiveBandwidthPct > 85    → OVERLOADED
  if effectiveBandwidthPct > 70    → BUSY
  else                             → AVAILABLE
```

Thresholds (85% and 70%) are configurable via `application.yml`.

### 9.3 Example Calculation

```
Developer: Arjun Kumar
Sprint: Mar 17 – Mar 28 (10 working days)
Assignments: CRM Core 50%, Analytics 35%, Tech Debt 10% → total = 95%
Leaves: None → leaveFraction = 0/10 = 0.0

effectiveBandwidthPct = 95 × (1 - 0.0) = 95%
Status: OVERLOADED (>85%)
```

```
Developer: Siddharth M.
Sprint: Mar 17 – Mar 28 (10 working days)
Assignments: Integrations 40%
Leaves: Mar 24–26 (3 working days) → leaveFraction = 3/10 = 0.3

effectiveBandwidthPct = 40 × (1 - 0.3) = 28%
Status: AVAILABLE (<70%)
```

### 9.4 Edge Cases

| Scenario | Handling |
|----------|----------|
| No assignments | totalAllocationPct = 0 → AVAILABLE |
| 100% leave in period | leaveFraction = 1.0 → effectivePct = 0 → ON_LEAVE |
| Allocation > 100% | Allowed (manager decides), flagged in alerts |
| Overlapping leaves | Each leave counted independently; days capped at workingDays |
| Weekend leaves | Ignored (only Mon–Fri counted) |
| Open-ended assignment | `endDate = null` means perpetually active |

---

## 10. Jira Integration

### 10.1 Pull Model

```
┌──────────────┐     Every 15 min (configurable)     ┌───────────────┐
│  BandViz     │ ───── GET /rest/api/3/search ───▶   │  Jira Cloud   │
│  (scheduled) │ ◀─── JSON response ──────────────   │  REST API v3  │
└──────┬───────┘                                      └───────────────┘
       │
       │ Upsert
       ▼
  ┌────────────┐
  │ jira_tickets│
  │   table     │
  └────────────┘
```

### 10.2 Sync Flow

```
1. JiraSyncService.syncAll() fires every 15 min (or on-demand via POST /api/jira/sync)
2. Load all active projects that have a jiraProjectKey
3. For each project:
   a. Build JQL: "project = {KEY} AND statusCategory != Done ORDER BY updated DESC"
   b. Call Jira Search API with pagination (100 per page)
   c. For each issue:
      - Find existing JiraTicket by ticketKey, or create new
      - Map Jira fields → BandViz fields
      - Save (upsert)
4. Log sync result (count, errors)
5. Update lastSyncedAt timestamp
```

### 10.3 Field Mapping

| Jira Field              | BandViz Field            | Mapping Logic                      |
|-------------------------|--------------------------|------------------------------------|
| `key`                   | `ticketKey`              | Direct                             |
| `fields.summary`        | `summary`                | Direct                             |
| `fields.assignee.email` | `assigneeJiraUsername`   | Matched to Developer.jiraUsername   |
| `fields.status.name`    | `status`                 | Map: "In Progress"→IN_PROGRESS, etc|
| `fields.priority.name`  | `priority`               | Map: "High"→HIGH, etc              |
| `customfield_10016`     | `storyPoints`            | Jira story points custom field     |
| `customfield_10020[].name` | `sprintName`          | First active sprint name           |

### 10.4 Status Mapping

| Jira Status Name          | BandViz TicketStatus |
|---------------------------|----------------------|
| To Do, Open, New          | `TO_DO`              |
| In Progress               | `IN_PROGRESS`        |
| In Review, Code Review    | `IN_REVIEW`          |
| Blocked                   | `BLOCKED`            |
| Done, Closed, Resolved    | `DONE`               |

### 10.5 Authentication
- **Method:** Basic Auth over HTTPS
- **Credentials:** Jira email + API token (stored in env variables, not in code)
- **Permissions required:** Read-only access to project issues

### 10.6 Error Handling
- 429 (Rate Limit): Log warning, skip project, retry on next scheduled run
- 401 (Auth failure): Log error, mark sync as failed, surface in `/api/jira/status`
- Network timeout: 30-second timeout configured on RestTemplate, skip and retry

---

## 11. Key Sequence Diagrams

### 11.1 Dashboard Load

```
Browser                Controller          DashboardService      BandwidthService       DB
  │                        │                     │                     │                 │
  │ GET /api/dashboard     │                     │                     │                 │
  │───────────────────────▶│                     │                     │                 │
  │                        │ getActiveSprint()   │                     │                 │
  │                        │────────────────────▶│                     │                 │
  │                        │                     │ find active sprint  │                 │
  │                        │                     │────────────────────────────────────────▶│
  │                        │                     │◀───────────────── Sprint ──────────────│
  │                        │                     │                     │                 │
  │                        │                     │ computeForActiveSprint()              │
  │                        │                     │────────────────────▶│                 │
  │                        │                     │                     │ findAllActive   │
  │                        │                     │                     │ Developers      │
  │                        │                     │                     │────────────────▶│
  │                        │                     │                     │◀───────────────│
  │                        │                     │                     │                 │
  │                        │                     │                     │ findAllActive   │
  │                        │                     │                     │ Assignments     │
  │                        │                     │                     │────────────────▶│
  │                        │                     │                     │◀───────────────│
  │                        │                     │                     │                 │
  │                        │                     │                     │ For each dev:   │
  │                        │                     │                     │ countLeaveDays  │
  │                        │                     │                     │ + fetchJira     │
  │                        │                     │                     │────────────────▶│
  │                        │                     │                     │◀───────────────│
  │                        │                     │                     │                 │
  │                        │                     │◀── List<DevBW> ────│                 │
  │                        │                     │                     │                 │
  │                        │                     │ buildAlerts()       │                 │
  │                        │                     │ countJiraTickets()  │                 │
  │                        │                     │──────────────────────────────────────▶│
  │                        │                     │◀────────────────────────────────────│ │
  │                        │                     │                     │                 │
  │                        │◀── DashboardResponse│                     │                 │
  │◀──── 200 JSON ────────│                     │                     │                 │
```

### 11.2 Leave Approval → Bandwidth Impact

```
Manager                   LeaveController     LeaveService          DB
  │                            │                   │                 │
  │ POST /api/leaves/5/approve │                   │                 │
  │───────────────────────────▶│                   │                 │
  │                            │ approve(5)        │                 │
  │                            │──────────────────▶│                 │
  │                            │                   │ findById(5)     │
  │                            │                   │────────────────▶│
  │                            │                   │◀─── Leave ─────│
  │                            │                   │                 │
  │                            │                   │ validate PENDING│
  │                            │                   │ set APPROVED    │
  │                            │                   │                 │
  │                            │                   │ save            │
  │                            │                   │────────────────▶│
  │                            │                   │◀────────────────│
  │                            │◀── LeaveResponse ─│                 │
  │◀──── 200 JSON ────────────│                   │                 │
  │                            │                   │                 │
  │  [Next time /dashboard is called, BandwidthService will include │
  │   this leave in leaveFraction, reducing the dev's effective %]  │
```

### 11.3 Jira Sync

```
Scheduler / Manager         JiraSyncService       JiraClient        Jira Cloud       DB
  │                              │                     │                │              │
  │ (scheduled or POST)          │                     │                │              │
  │─────────────────────────────▶│                     │                │              │
  │                              │ load active projects│                │              │
  │                              │─────────────────────────────────────────────────────▶│
  │                              │◀── projects with keys ──────────────────────────────│
  │                              │                     │                │              │
  │                              │ for each projectKey:│                │              │
  │                              │ fetchOpenIssues()   │                │              │
  │                              │────────────────────▶│                │              │
  │                              │                     │ GET /search    │              │
  │                              │                     │───────────────▶│              │
  │                              │                     │◀── JSON ───────│              │
  │                              │◀── JiraSearchResp ──│                │              │
  │                              │                     │                │              │
  │                              │ for each issue:     │                │              │
  │                              │ findByTicketKey()   │                │              │
  │                              │──────────────────────────────────────────────────── ▶│
  │                              │◀── existing or null ────────────────────────────────│
  │                              │                     │                │              │
  │                              │ map fields + save   │                │              │
  │                              │─────────────────────────────────────────────────────▶│
  │                              │                     │                │              │
  │◀──── SyncResult ────────────│                     │                │              │
```

---

## 12. Error Handling

### 12.1 Exception Hierarchy

```
RuntimeException
  ├── ResourceNotFoundException    → 404 Not Found
  ├── BusinessException            → 422 Unprocessable Entity
  └── (Spring's MethodArgumentNotValidException → 400 Bad Request)
```

### 12.2 Global Error Response Format

All errors return a consistent JSON shape via `@ControllerAdvice`:

```json
{
  "timestamp": "2026-03-25T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Developer not found: 99",
  "path": "/api/developers/99"
}
```

### 12.3 Validation Strategy

| Layer      | What's validated                         | How                            |
|------------|------------------------------------------|--------------------------------|
| Controller | Request body shape, required fields      | `@Valid` + Jakarta annotations |
| Service    | Business rules (e.g., no duplicate email)| `BusinessException`            |
| Repository | DB constraints (unique, FK)              | Caught and mapped in service   |

---

## 13. Configuration

### 13.1 application.yml Key Properties

```yaml
# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/bandviz
spring.datasource.username: ${DB_USERNAME}
spring.datasource.password: ${DB_PASSWORD}

# Jira (all from env variables)
bandviz.jira.base-url: ${JIRA_BASE_URL}
bandviz.jira.email: ${JIRA_EMAIL}
bandviz.jira.api-token: ${JIRA_API_TOKEN}
bandviz.jira.sync-enabled: ${JIRA_SYNC_ENABLED:false}

# Bandwidth thresholds
bandviz.bandwidth.overload-threshold: 85
bandviz.bandwidth.busy-threshold: 70
bandviz.bandwidth.default-weekly-capacity-hours: 40
```

### 13.2 Profiles

| Profile  | Purpose                       | DB          | Jira  |
|----------|-------------------------------|-------------|-------|
| `default`| Local development             | PostgreSQL  | Disabled |
| `test`   | Unit/integration tests        | H2 in-memory| Disabled |
| `prod`   | Production                    | PostgreSQL  | Enabled  |

---

## 14. Security Considerations

### 14.1 v1 (No Auth)
- Application runs on internal network only
- No authentication or authorization
- Jira API token stored in environment variables (never in code or config files)
- CORS configured to allow only the frontend origin

### 14.2 v2 (Planned)
- Spring Security + JWT for manager login
- Role-based access: MANAGER (full access) vs DEVELOPER (view own data, apply leave)
- API rate limiting on sync endpoint

---

## 15. Future Enhancements

| Priority | Enhancement | Notes |
|----------|-------------|-------|
| P1 | JWT auth + role-based access | MANAGER vs DEVELOPER roles |
| P1 | Email notifications for overload alerts | Spring Mail |
| P2 | Slack integration for alerts | Webhook-based |
| P2 | Sprint burndown chart data endpoint | For frontend chart rendering |
| P2 | Historical bandwidth tracking | Store weekly snapshots |
| P3 | Jira webhook for real-time sync | Replace polling with push |
| P3 | CSV/Excel export | Dashboard + bandwidth data |
| P3 | Public holiday calendar (per region) | Currently handled via LeaveType.PUBLIC_HOLIDAY |
| P3 | Bandwidth forecasting for next sprint | Based on trending allocation + planned leaves |

---

## Flyway Migrations

Migration files to be created in `src/main/resources/db/migration/`:

```
V1__create_developers.sql
V2__create_projects.sql
V3__create_sprints.sql
V4__create_assignments.sql
V5__create_leaves.sql
V6__create_jira_tickets.sql
V7__create_indexes.sql
V8__insert_sample_data.sql
```

Each migration is idempotent and forward-only (no rollbacks in Flyway by default).

---

*End of LLD*
