package fr.walid.acd.contribution.adapters.persistence;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DocumentSchemaMigrationIT {

    private static final String[] TABLES = {"documents", "document_sections", "document_audit",
            "document_versions", "document_version_sections"};

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine");

    @Test
    void initialSchemaCanBeRolledBackAndReappliedInADisposableDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                for (String table : TABLES) {
                    assertThat(tableExists(connection, table)).as(table).isTrue();
                }

                // Two changesets: the aggregate and the stored versions.
                liquibase.rollback(2, new Contexts(), new LabelExpression());
                for (String table : TABLES) {
                    assertThat(tableExists(connection, table)).as(table).isFalse();
                }

                liquibase.update(new Contexts(), new LabelExpression());
                for (String table : TABLES) {
                    assertThat(tableExists(connection, table)).as(table).isTrue();
                }
            }
        }
    }

    private static boolean tableExists(Connection connection, String name) throws Exception {
        try (var query = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            query.setString(1, name);
            try (var result = query.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }
}
