package com.jada.severe.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** One transaction and a database advisory lock protect the complete migration sequence. */
@Configuration
public class DatabaseMigration implements InitializingBean {
    private final DataSource source;
    public DatabaseMigration(DataSource source) { this.source = source; }
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("select current_database()")) {
                    result.next();
                    if (!"severe_equipment_assets".equals(result.getString(1))) throw new IllegalStateException("Only severe_equipment_assets is allowed");
                }
                try (var statement = connection.createStatement()) {
                    statement.execute("select pg_advisory_xact_lock(7341289012234)");
                }
                migrate(connection, "metadata-v1", "db/metadata.sql");
                migrate(connection, "business-v1", "db/business.sql");
                migrate(connection, "business-v2", "db/legacy-states.sql");
                migrate(connection, "business-v3", "db/stock-actions.sql");
                migrate(connection, "business-v4", "db/production.sql");
                migrate(connection, "business-v5", "db/release-hardening.sql");
                migrate(connection, "business-v6", "db/final-constraints.sql");
                migrate(connection, "business-v7", "db/role-delivery.sql");
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }
    private void migrate(Connection connection, String version, String resource) throws Exception {
        boolean table;
        try (var statement = connection.createStatement(); var result = statement.executeQuery("select to_regclass('public.app_schema_migrations') is not null")) {
            result.next(); table = result.getBoolean(1);
        }
        if (table) {
            try (PreparedStatement statement = connection.prepareStatement("select exists(select 1 from app_schema_migrations where version=?)")) {
                statement.setString(1, version);
                try (ResultSet result = statement.executeQuery()) { result.next(); if (result.getBoolean(1)) return; }
            }
        }
        String sql;
        try (var input = new ClassPathResource(resource).getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^BEGIN;\\s*$", "").replaceAll("(?m)^COMMIT;\\s*$", "");
        }
        ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.support.EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
}
