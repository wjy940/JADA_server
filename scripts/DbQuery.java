import java.sql.*;
/** Read-only acceptance queries against the explicitly selected business database. */
class DbQuery {
    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:55432/severe_equipment_assets");
        String user = System.getenv().getOrDefault("DB_USERNAME", "jd_admin");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "jd_dev_password");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select current_database()")) {
                result.next();
                if (!"severe_equipment_assets".equals(result.getString(1))) throw new IllegalStateException("Unexpected database");
            }
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(args[0])) {
                while (result.next()) System.out.println(result.getString(1));
            }
        }
    }
}
