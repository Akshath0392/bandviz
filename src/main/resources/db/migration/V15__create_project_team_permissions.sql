CREATE TABLE project_team_permissions (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    PRIMARY KEY (project_id, team_id)
);

INSERT INTO project_team_permissions (project_id, team_id)
SELECT id, team_id
FROM projects
WHERE team_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX idx_project_team_permissions_team_id ON project_team_permissions(team_id);
CREATE INDEX idx_project_team_permissions_project_id ON project_team_permissions(project_id);
