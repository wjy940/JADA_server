BEGIN;
-- Preserve imported display/operational status. Never infer an approval decision from static prototype text.
WITH old AS MATERIALIZED (
 SELECT id,module_id,status,to_jsonb(r) AS snapshot FROM app_records r
 WHERE status NOT IN ('draft','submitted','reviewing','approved','rejected','voided','archived') FOR UPDATE
), changed AS (
 UPDATE app_records r SET payload=r.payload || jsonb_build_object('legacyStatus',r.status,'businessStatus',r.status,'legacyNeedsReview',true),status='draft',updated_by='schema-migration',updated_at=now()
 FROM old WHERE r.id=old.id RETURNING r.id,r.module_id,to_jsonb(r) AS snapshot
)
INSERT INTO app_audit_logs(operator,role,module_id,record_id,action,before_status,after_status,before_data,after_data,request_no,result)
 SELECT 'schema-migration','system',old.module_id,old.id,'legacyStateMigration',old.status,'draft',old.snapshot,changed.snapshot,'business-v2','success'
 FROM old JOIN changed ON changed.id=old.id;
INSERT INTO app_schema_migrations(version) VALUES('business-v2') ON CONFLICT DO NOTHING;
COMMIT;
