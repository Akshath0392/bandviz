CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE projects
    ADD COLUMN team_id BIGINT REFERENCES teams(id);

ALTER TABLE developers
    ADD COLUMN team_id BIGINT REFERENCES teams(id);

CREATE INDEX idx_projects_team_id ON projects(team_id);
CREATE INDEX idx_developers_team_id ON developers(team_id);
