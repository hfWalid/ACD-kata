package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.adapters.importing.ImportReportJson;
import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.ImportReport;
import fr.walid.acd.contribution.application.importing.RowOutcome;
import fr.walid.acd.contribution.application.importing.RowStatus;
import fr.walid.acd.contribution.application.port.in.DocumentImport;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.ImportSource;
import fr.walid.acd.contribution.domain.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Command line import: reads one file, prints what happened to each record, writes the report and
 * waits for the outbox to drain so the events of the imported documents are actually published
 * before the process exits.
 *
 * <p>Exit code 0 means every record was imported or already there; 2 means at least one record was
 * refused, and the report says which.
 */
@Component
@ConditionalOnProperty(prefix = "acd.import", name = "file")
class ImportRunner implements ApplicationRunner, ExitCodeGenerator {
    static final int REFUSED = 2;
    private static final Logger LOG = LoggerFactory.getLogger(ImportRunner.class);
    private static final long DRAIN_POLL_MILLIS = 200;

    private final ImportProperties properties;
    private final ImportSource importSource;
    private final DocumentImport importer;
    private final EventOutbox outbox;
    private final ImportReportJson reportWriter = new ImportReportJson();
    private int exitCode;

    ImportRunner(ImportProperties properties, ImportSource importSource, DocumentImport importer, EventOutbox outbox) {
        this.properties = properties;
        this.importSource = importSource;
        this.importer = importer;
        this.outbox = outbox;
    }

    @Override
    public void run(ApplicationArguments args) {
        var file = properties.file();
        try {
            var batch = importSource.read(properties.source(), file.getFileName().toString(), Files.readAllBytes(file));
            var options = new ImportOptions(new Actor(properties.actor()), new Actor(properties.approverOrActor()),
                    properties.mode(), properties.dryRun());
            var report = importer.importDocuments(batch, options);
            System.out.println(summary(report));
            System.out.print(detail(report));
            writeReport(report);
            var stillPending = awaitDrain();
            if (stillPending > 0) {
                LOG.warn("{} event(s) still pending after the drain timeout", stillPending);
            }
            exitCode = report.hasRejections() ? REFUSED : 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ImportBatchRejectedException e) {
            System.out.println("Import refusé (" + e.code() + ") : " + e.getMessage());
            exitCode = REFUSED;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private String summary(ImportReport report) {
        return "%s — %d importé(s), %d déjà présent(s), %d rejeté(s), %d planifié(s)%s%n".formatted(
                report.source(), report.count(RowStatus.IMPORTED), report.count(RowStatus.ALREADY_IMPORTED),
                report.count(RowStatus.REJECTED), report.count(RowStatus.PLANNED),
                report.dryRun() ? " [simulation]" : "");
    }

    private String detail(ImportReport report) {
        return report.rows().stream()
                .filter(row -> row.status() != RowStatus.IMPORTED && row.status() != RowStatus.PLANNED)
                .sorted(Comparator.comparingInt(RowOutcome::line))
                .map(row -> "  ligne %d %s → %s %s%n".formatted(row.line(),
                        row.reference() == null ? "(sans référence)" : row.reference(), row.status(),
                        row.code() == null ? "" : row.code() + " : " + row.detail()))
                .reduce("", String::concat);
    }

    private void writeReport(ImportReport report) {
        var reportFile = properties.report();
        try {
            var parent = reportFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(reportFile, reportWriter.write(report) + "\n", StandardCharsets.UTF_8);
            System.out.println("Rapport écrit dans " + reportFile.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The relay of this process publishes the events the import just appended. Waiting for the
     * outbox to empty keeps the command honest: when it returns, the review service has seen the
     * documents it announced.
     */
    private long awaitDrain() {
        if (properties.dryRun() || properties.drainTimeout().isZero()) {
            return outbox.countPending();
        }
        var deadline = System.nanoTime() + properties.drainTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            var pending = outbox.countPending();
            if (pending == 0) {
                return 0;
            }
            sleep();
        }
        return outbox.countPending();
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(DRAIN_POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
