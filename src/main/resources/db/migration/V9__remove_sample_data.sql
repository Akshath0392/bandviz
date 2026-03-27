DELETE FROM jira_tickets
WHERE ticket_key IN (
    'CRM-101',
    'CRM-109',
    'ANL-55',
    'INT-72',
    'PLT-33',
    'CRM-145',
    'ANL-59'
);

DELETE FROM leaves
WHERE notes IN (
    'Family vacation',
    'Fever',
    'Wedding travel'
);

DELETE FROM assignments
WHERE (developer_id, project_id, start_date) IN (
    (1, 1, DATE '2026-03-17'),
    (1, 2, DATE '2026-03-17'),
    (2, 3, DATE '2026-03-17'),
    (2, 1, DATE '2026-03-17'),
    (3, 2, DATE '2026-03-17'),
    (4, 1, DATE '2026-03-17'),
    (5, 4, DATE '2026-03-17'),
    (6, 1, DATE '2026-03-17'),
    (6, 3, DATE '2026-03-17'),
    (7, 2, DATE '2026-03-17'),
    (8, 3, DATE '2026-03-17')
);

DELETE FROM sprints
WHERE (name, start_date, end_date) IN (
    ('Sprint 41', DATE '2026-03-03', DATE '2026-03-14'),
    ('Sprint 42', DATE '2026-03-17', DATE '2026-03-28')
);

DELETE FROM projects
WHERE jira_project_key IN ('CRM', 'ANL', 'INT', 'PLT')
  AND name IN ('CRM Core', 'Analytics 2.0', 'Integrations', 'Platform Reliability');

DELETE FROM developers
WHERE email IN (
    'arjun.k@vymo.com',
    'siddharth.m@vymo.com',
    'neha.r@vymo.com',
    'ritika.s@vymo.com',
    'vijay.p@vymo.com',
    'ishita.j@vymo.com',
    'karan.m@vymo.com',
    'aman.v@vymo.com'
);
