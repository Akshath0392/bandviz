INSERT INTO teams (name, description, active)
VALUES ('Unassigned', 'Auto-created fallback team for legacy rows', TRUE)
ON CONFLICT (name) DO NOTHING;

UPDATE developers
SET team_id = (SELECT id FROM teams WHERE name = 'Unassigned')
WHERE team_id IS NULL;

UPDATE projects
SET team_id = (SELECT id FROM teams WHERE name = 'Unassigned')
WHERE team_id IS NULL;

ALTER TABLE developers
    ALTER COLUMN team_id SET NOT NULL;

ALTER TABLE projects
    ALTER COLUMN team_id SET NOT NULL;
