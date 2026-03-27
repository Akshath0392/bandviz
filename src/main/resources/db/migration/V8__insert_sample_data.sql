INSERT INTO developers (id, name, email, role, weekly_capacity_hours, jira_username, active) VALUES
(1, 'Arjun Kumar', 'arjun.k@vymo.com', 'SENIOR_BACKEND_ENGINEER', 40, 'arjun.k@vymo.com', TRUE),
(2, 'Siddharth M', 'siddharth.m@vymo.com', 'FULL_STACK_ENGINEER', 40, 'siddharth.m@vymo.com', TRUE),
(3, 'Neha Rao', 'neha.r@vymo.com', 'FRONTEND_ENGINEER', 40, 'neha.r@vymo.com', TRUE),
(4, 'Ritika Shah', 'ritika.s@vymo.com', 'QA_ENGINEER', 40, 'ritika.s@vymo.com', TRUE),
(5, 'Vijay Pratap', 'vijay.p@vymo.com', 'DEVOPS_ENGINEER', 40, 'vijay.p@vymo.com', TRUE),
(6, 'Ishita Jain', 'ishita.j@vymo.com', 'BACKEND_ENGINEER', 40, 'ishita.j@vymo.com', TRUE),
(7, 'Karan Mehta', 'karan.m@vymo.com', 'SENIOR_FRONTEND_ENGINEER', 40, 'karan.m@vymo.com', TRUE),
(8, 'Aman Verma', 'aman.v@vymo.com', 'FULL_STACK_ENGINEER', 40, 'aman.v@vymo.com', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO projects (id, name, jira_project_key, color, target_utilization_pct, active) VALUES
(1, 'CRM Core', 'CRM', '#6366f1', 70, TRUE),
(2, 'Analytics 2.0', 'ANL', '#a855f7', 70, TRUE),
(3, 'Integrations', 'INT', '#14b8a6', 75, TRUE),
(4, 'Platform Reliability', 'PLT', '#f97316', 65, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sprints (id, name, start_date, end_date, active) VALUES
(1, 'Sprint 41', DATE '2026-03-03', DATE '2026-03-14', FALSE),
(2, 'Sprint 42', DATE '2026-03-17', DATE '2026-03-28', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO assignments (developer_id, project_id, allocation_pct, start_date, end_date) VALUES
(1, 1, 50, DATE '2026-03-17', NULL),
(1, 2, 45, DATE '2026-03-17', NULL),
(2, 3, 40, DATE '2026-03-17', NULL),
(2, 1, 20, DATE '2026-03-17', NULL),
(3, 2, 75, DATE '2026-03-17', NULL),
(4, 1, 80, DATE '2026-03-17', NULL),
(5, 4, 90, DATE '2026-03-17', NULL),
(6, 1, 60, DATE '2026-03-17', NULL),
(6, 3, 20, DATE '2026-03-17', NULL),
(7, 2, 85, DATE '2026-03-17', NULL),
(8, 3, 50, DATE '2026-03-17', NULL)
ON CONFLICT (developer_id, project_id, start_date) DO NOTHING;

INSERT INTO leaves (developer_id, leave_type, start_date, end_date, status, notes) VALUES
(2, 'PLANNED', DATE '2026-03-24', DATE '2026-03-26', 'APPROVED', 'Family vacation'),
(4, 'SICK', DATE '2026-03-25', DATE '2026-03-25', 'APPROVED', 'Fever'),
(6, 'PLANNED', DATE '2026-03-27', DATE '2026-03-28', 'PENDING', 'Wedding travel')
ON CONFLICT DO NOTHING;

INSERT INTO jira_tickets (ticket_key, summary, assignee_jira_username, story_points, status, priority, project_key, sprint_name, last_synced_at) VALUES
('CRM-101', 'Fix CRM sync timeout', 'arjun.k@vymo.com', 5, 'IN_PROGRESS', 'HIGH', 'CRM', 'Sprint 42', NOW()),
('CRM-109', 'Refactor lead assignment module', 'arjun.k@vymo.com', 8, 'BLOCKED', 'HIGH', 'CRM', 'Sprint 42', NOW()),
('ANL-55', 'Dashboard chart rendering issue', 'neha.r@vymo.com', 3, 'IN_REVIEW', 'MEDIUM', 'ANL', 'Sprint 42', NOW()),
('INT-72', 'Salesforce API contract mismatch', 'siddharth.m@vymo.com', 5, 'TO_DO', 'HIGH', 'INT', 'Sprint 42', NOW()),
('PLT-33', 'K8s node autoscaling alerts', 'vijay.p@vymo.com', 8, 'IN_PROGRESS', 'HIGHEST', 'PLT', 'Sprint 42', NOW()),
('CRM-145', 'Regression in onboarding flow', 'ritika.s@vymo.com', 2, 'TO_DO', 'MEDIUM', 'CRM', 'Sprint 42', NOW()),
('ANL-59', 'Story points cleanup', 'karan.m@vymo.com', 3, 'DONE', 'LOW', 'ANL', 'Sprint 42', NOW())
ON CONFLICT (ticket_key) DO NOTHING;

SELECT setval('developers_id_seq', COALESCE((SELECT MAX(id) FROM developers), 1));
SELECT setval('projects_id_seq', COALESCE((SELECT MAX(id) FROM projects), 1));
SELECT setval('sprints_id_seq', COALESCE((SELECT MAX(id) FROM sprints), 1));
