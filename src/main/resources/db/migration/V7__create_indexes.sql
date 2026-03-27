CREATE INDEX IF NOT EXISTS idx_assignments_developer_dates
    ON assignments (developer_id, start_date, end_date);

CREATE INDEX IF NOT EXISTS idx_leaves_dev_dates_status
    ON leaves (developer_id, start_date, end_date, status);

CREATE INDEX IF NOT EXISTS idx_jira_tickets_assignee
    ON jira_tickets (assignee_jira_username);

CREATE INDEX IF NOT EXISTS idx_jira_tickets_project
    ON jira_tickets (project_key);
