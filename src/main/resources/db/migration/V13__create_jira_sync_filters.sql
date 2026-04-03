CREATE TABLE IF NOT EXISTS jira_sync_filters (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(20) NOT NULL,
    project_key VARCHAR(64) NOT NULL,
    assignees TEXT,
    labels TEXT,
    sprint_mode VARCHAR(64),
    status_category VARCHAR(64),
    components TEXT,
    issue_types TEXT,
    priority_mode VARCHAR(64),
    created_after DATE,
    custom_jql TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_jira_sync_filters_scope_project UNIQUE (scope, project_key)
);

CREATE INDEX IF NOT EXISTS idx_jira_sync_filters_scope
    ON jira_sync_filters (scope);
