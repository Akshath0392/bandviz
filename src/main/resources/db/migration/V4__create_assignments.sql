CREATE TABLE IF NOT EXISTS assignments (
    id BIGSERIAL PRIMARY KEY,
    developer_id BIGINT NOT NULL REFERENCES developers(id),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    allocation_pct INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    CONSTRAINT uq_assignment_dev_project_dates UNIQUE (developer_id, project_id, start_date)
);
