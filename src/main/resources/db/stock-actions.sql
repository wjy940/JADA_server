BEGIN;
ALTER TABLE app_stock_movements ADD COLUMN IF NOT EXISTS action text NOT NULL DEFAULT 'inbound';
ALTER TABLE app_stock_movements ADD COLUMN IF NOT EXISTS target_warehouse_id text;
INSERT INTO app_schema_migrations(version) VALUES('business-v3') ON CONFLICT DO NOTHING;
COMMIT;
