CREATE TABLE IF NOT EXISTS jira_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_key VARCHAR(30) NOT NULL UNIQUE,
    summary TEXT,
    assignee_jira_username VARCHAR(100),
    story_points INTEGER,
    status VARCHAR(30),
    priority VARCHAR(20),
    project_key VARCHAR(20),
    sprint_name VARCHAR(100),
    last_synced_at TIMESTAMP
);
