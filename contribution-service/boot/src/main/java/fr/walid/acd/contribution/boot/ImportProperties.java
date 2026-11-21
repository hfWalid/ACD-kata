package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.importing.ImportMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Import configuration. The adapter settings (where the mapping specs live, how many documents a
 * file may contain) apply to the web endpoint too; the run settings only feed the command line.
 */
@ConfigurationProperties(prefix = "acd.import")
public record ImportProperties(
        @DefaultValue("contracts/import") Path specsDirectory,
        @DefaultValue("10000") int maxRows,
        @DefaultValue("10s") Duration drainTimeout,
        @DefaultValue("annuaire") String source,
        @DefaultValue("DRAFT") ImportMode mode,
        @DefaultValue("importateur") String actor,
        String approver,
        Path file,
        @DefaultValue("target/import-report.json") Path report,
        @DefaultValue("false") boolean dryRun) {

    public ImportProperties {
        if (maxRows < 1) {
            throw new IllegalArgumentException("acd.import.max-rows must be positive");
        }
    }

    /** The approver recorded on approvals; the actor when the run does not name one. */
    public String approverOrActor() {
        return approver == null || approver.isBlank() ? actor : approver;
    }
}
