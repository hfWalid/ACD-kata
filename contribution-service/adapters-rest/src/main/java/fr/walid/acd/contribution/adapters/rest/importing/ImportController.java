package fr.walid.acd.contribution.adapters.rest.importing;

import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;
import fr.walid.acd.contribution.application.importing.ImportMode;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.ImportReport;
import fr.walid.acd.contribution.application.port.in.DocumentImport;
import fr.walid.acd.contribution.application.port.out.ImportSource;
import fr.walid.acd.contribution.domain.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * The same import the command line runs, reached with a file upload. It returns the report instead
 * of an exit code, and it creates, submits and approves the documents through the normal commands:
 * this endpoint has no shortcut of its own.
 */
@RestController
@RequestMapping("/api/imports")
@Tag(name = "Imports", description = "CSV and JSON file import")
public class ImportController {
    private final DocumentImport importer;
    private final ImportSource importSource;

    public ImportController(DocumentImport importer, ImportSource importSource) {
        this.importer = importer;
        this.importSource = importSource;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import a CSV or JSON file",
            description = "Maps the file with the spec declared for the source, then runs the existing commands for "
                    + "each record. A record refused by the domain is reported with its code; the other records are "
                    + "still imported.")
    @ApiResponse(responseCode = "200", description = "Import report",
            content = @Content(schema = @Schema(implementation = ImportReportResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unknown source, unsupported format or unknown mode",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ImportReportResponse importFile(
            @Parameter(description = "Author identifier for this local kata; not authentication")
            @RequestHeader("X-User") @NotBlank @Size(max = 100) String actor,
            @Parameter(description = "Actor recorded on approvals; the author when omitted")
            @RequestHeader(name = "X-Approver", required = false) @Size(max = 100) String approver,
            @Parameter(description = "Import source, which selects the mapping under contracts/import")
            @RequestParam String source,
            @Parameter(description = "DRAFT, SUBMITTED or APPROVED")
            @RequestParam(defaultValue = "DRAFT") String mode,
            @Parameter(description = "Simulate the workflow without writing anything")
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestPart("file") MultipartFile file) {
        var options = new ImportOptions(new Actor(actor), new Actor(approver(actor, approver)),
                mode(mode), dryRun);
        ImportReport report = importer.importDocuments(read(source, file), options);
        return ImportReportResponse.from(report);
    }

    private fr.walid.acd.contribution.application.importing.ImportBatch read(String source, MultipartFile file) {
        try {
            return importSource.read(source, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ImportBatchRejectedException("UNREADABLE_FILE", "Fichier illisible : " + e.getMessage());
        }
    }

    private static String approver(String actor, String approver) {
        return approver == null || approver.isBlank() ? actor : approver;
    }

    private static ImportMode mode(String mode) {
        try {
            return ImportMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ImportBatchRejectedException("UNKNOWN_MODE", "Mode d'import inconnu : " + mode);
        }
    }
}
