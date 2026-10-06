BEGIN;
ALTER TABLE app_records ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false;
ALTER TABLE app_roles ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false;
UPDATE app_users SET is_test=true WHERE id LIKE 'QA-%';
UPDATE app_roles SET is_test=true WHERE id LIKE 'QA-%';
UPDATE app_records SET is_test=true WHERE title LIKE 'QA-%' OR payload->>'assetNo' LIKE 'QA-%' OR payload->>'资产编号' LIKE 'QA-%';
WITH RECURSIVE qa(id) AS (
 SELECT id FROM app_records WHERE is_test UNION SELECT l.target_id FROM app_business_links l JOIN qa ON qa.id=l.source_id
) UPDATE app_records SET is_test=true WHERE id IN (SELECT id FROM qa);
INSERT INTO app_role_permissions SELECT r.id,p.id FROM app_roles r CROSS JOIN app_permissions p WHERE r.id IN ('internal','finance','procurement','equipmentManager','warehouse','supplier','driver','operator','approver') AND p.module_id IN ('dashboard','notice') AND p.action='view' ON CONFLICT DO NOTHING;
INSERT INTO app_role_permissions SELECT r.id,p.id FROM app_roles r CROSS JOIN app_permissions p WHERE r.id IN ('finance','equipmentManager','warehouse','procurement') AND p.module_id IN ('approval','expenseTodo') AND p.action='view' ON CONFLICT DO NOTHING;
INSERT INTO app_schema_migrations(version) VALUES('business-v5') ON CONFLICT DO NOTHING;
COMMIT;
