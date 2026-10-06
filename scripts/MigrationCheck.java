import java.sql.*;
import java.nio.file.*;
import java.util.*;
/** Validate every migration in an isolated temporary schema in the required database, then roll back. */
class MigrationCheck {
 public static void main(String[] args) throws Exception {
  String url=System.getenv().getOrDefault("DB_URL","jdbc:postgresql://127.0.0.1:55432/severe_equipment_assets");
  try(Connection connection=DriverManager.getConnection(url,System.getenv().getOrDefault("DB_USERNAME","jd_admin"),System.getenv().getOrDefault("DB_PASSWORD","jd_dev_password"))){
   connection.setAutoCommit(false);
   try(Statement sql=connection.createStatement()){
    try(ResultSet r=sql.executeQuery("select current_database()")){r.next();if(!r.getString(1).equals("severe_equipment_assets"))throw new IllegalStateException("Unexpected database");}
    String schema="qa_migration_"+UUID.randomUUID().toString().replace("-","");
    sql.execute("create schema "+schema);sql.execute("set local search_path to "+schema);
    for(String file:List.of("metadata.sql","business.sql","legacy-states.sql","stock-actions.sql","production.sql","release-hardening.sql","final-constraints.sql","role-delivery.sql")){
     String source=Files.readString(Path.of("src/main/resources/db",file)).replaceAll("(?m)^BEGIN;\\s*$","").replaceAll("(?m)^COMMIT;\\s*$","");
     sql.execute(source);System.out.println("PASS fresh schema migration "+file);
    }
    try(ResultSet r=sql.executeQuery("select (select count(*) from app_modules),(select count(*) from app_records),(select count(*) from app_schema_migrations)")){r.next();if(r.getInt(1)!=35||r.getInt(2)!=0||r.getInt(3)!=8)throw new IllegalStateException("Unexpected initialized data");}
    System.out.println("PASS fresh initialization: 35 metadata modules, 0 business records, 8 migration versions");
   }finally{connection.rollback();}
   System.out.println("PASS isolated schema rolled back; public business database unchanged");
  }
 }
}
