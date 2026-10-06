BEGIN;
-- Finance reads source documents but cannot approve procurement/asset documents through these grants.
INSERT INTO app_role_permissions SELECT 'finance',id FROM app_permissions WHERE module_id IN ('partsPurchase','contract','rental','maintenance','workorder','expenseReimburse','outsourcePayment') AND action IN ('view','attachments') ON CONFLICT DO NOTHING;
INSERT INTO app_role_permissions SELECT 'finance',id FROM app_permissions WHERE module_id='expensePaymentRecord' AND action='void' ON CONFLICT DO NOTHING;
INSERT INTO app_role_permissions SELECT 'equipmentManager',id FROM app_permissions WHERE module_id IN ('person','vehicleEquipment','vehicleDispatch','machineDispatch','inspection','assetLife','standards') AND action IN ('view','create','edit','submit','approve','reject','archive','attachments','export') ON CONFLICT DO NOTHING;
INSERT INTO app_schema_migrations(version) VALUES('business-v7') ON CONFLICT DO NOTHING;
COMMIT;
