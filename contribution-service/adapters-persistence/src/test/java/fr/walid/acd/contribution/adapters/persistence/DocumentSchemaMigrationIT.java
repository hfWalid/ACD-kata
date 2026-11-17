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
                assertThat(tableExists(connection, "documents")).isTrue();
                assertThat(tableExists(connection, "document_sections")).isTrue();
                assertThat(tableExists(connection, "document_audit")).isTrue();

                liquibase.rollback(1, new Contexts(), new LabelExpression());
                assertThat(tableExists(connection, "documents")).isFalse();
                assertThat(tableExists(connection, "document_sections")).isFalse();
                assertThat(tableExists(connection, "document_audit")).isFalse();

                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(tableExists(connection, "documents")).isTrue();
                assertThat(tableExists(connection, "document_sections")).isTrue();
                assertThat(tableExists(connection, "document_audit")).isTrue();
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
