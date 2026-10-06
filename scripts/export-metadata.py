#!/usr/bin/env python3
"""Export only current metadata + empty record schema; no business/sample records or credentials."""
import json,os,pathlib,subprocess
java=os.getenv('JAVA_HOME','/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home')+'/bin/java'
driver=str(max(pathlib.Path.home().glob('.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar'),key=lambda p:tuple(int(n) for n in p.parent.name.split('.'))))
def query(sql):
 return json.loads(subprocess.check_output([java,'--class-path',driver,'scripts/DbQuery.java',sql],text=True))
def literal(value,typ):
 if value is None:return 'NULL'
 if isinstance(value,bool):return 'true' if value else 'false'
 if isinstance(value,(int,float)):return str(value)
 if isinstance(value,(dict,list)):value=json.dumps(value,ensure_ascii=False)
 return "'"+str(value).replace("'","''")+"'::"+typ
out=['-- Baseline from severe_equipment_assets; metadata only, zero business records.','BEGIN;']
for table in ['app_menu_groups','app_modules','app_module_columns','app_module_fields','app_records']:
 columns=query("select json_agg(x order by n) from (select a.attnum n,a.attname name,format_type(a.atttypid,a.atttypmod) type,a.attnotnull required,pg_get_expr(d.adbin,d.adrelid) default_value from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace ns on ns.oid=c.relnamespace left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum where ns.nspname='public' and c.relname='"+table+"' and a.attnum>0 and not a.attisdropped)x")
 constraints=query("select coalesce(json_agg(x),'[]') from (select conname name,pg_get_constraintdef(oid) definition from pg_constraint where conrelid='public."+table+"'::regclass order by conname)x")
 definitions=[]
 for c in columns:
  default=c['default_value']
  # Existing serial sequence must be declared on a fresh installation.
  typ='bigserial' if default and default.startswith('nextval(') and c['type']=='bigint' else ('serial' if default and default.startswith('nextval(') else c['type'])
  definitions.append('"'+c['name']+'" '+typ+(' NOT NULL' if c['required'] else '')+(' DEFAULT '+default if default and not default.startswith('nextval(') else ''))
 for c in constraints:definitions.append('CONSTRAINT "'+c['name']+'" '+c['definition'])
 out.append('CREATE TABLE IF NOT EXISTS '+table+' (\n '+',\n '.join(definitions)+'\n);')
 if table!='app_records':
  rows=query('select coalesce(json_agg(t),\'[]\') from '+table+' t')
  for r in rows:
   if table=='app_modules':r['stats']=[]
   names=[c['name'] for c in columns]
   out.append('INSERT INTO '+table+' ('+','.join('"'+n+'"' for n in names)+') VALUES ('+','.join(literal(r.get(c['name']),c['type']) for c in columns)+') ON CONFLICT DO NOTHING;')
out+=['CREATE TABLE IF NOT EXISTS app_schema_migrations(version text PRIMARY KEY,applied_at timestamptz NOT NULL DEFAULT now());',"INSERT INTO app_schema_migrations(version) VALUES('metadata-v1') ON CONFLICT DO NOTHING;",'COMMIT;']
pathlib.Path('src/main/resources/db/metadata.sql').write_text('\n'.join(out)+'\n')
print('Exported metadata baseline without app_records data')
