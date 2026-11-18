package fr.walid.acd.contribution.adapters.rest;

import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentSort;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.TemplateRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Document not found",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Version conflict or document rule violation",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
})
public class DocumentController {
    private final DocumentCommands commands;
    private final DocumentQueries queries;

    public DocumentController(DocumentCommands commands, DocumentQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft document")
    @ApiResponse(responseCode = "201", description = "Draft created",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public ResponseEntity<DocumentResponse> create(
            @Parameter(description = "Author identifier for this local kata; not authentication")
            @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
            @Valid @RequestBody CreateDocumentRequest request) {
        var document = commands.create(new CreateDocumentCommand(new TemplateRef(request.templateRef()), request.title(),
                request.sections().stream().map(SectionRequest::toDomain).toList(), new Actor(author)));
        return ResponseEntity.created(URI.create("/api/documents/" + document.id().value()))
                .body(DocumentResponse.from(document));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read a document and its audit trail")
    @ApiResponse(responseCode = "200", description = "Document and audit trail",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(queries.get(new DocumentId(id)));
    }

    @GetMapping("/{id}/versions/{version}")
    @Operation(summary = "Read the values stored for one document version")
    @ApiResponse(responseCode = "200", description = "Values of the requested version",
            content = @Content(schema = @Schema(implementation = DocumentVersionResponse.class)))
    public DocumentVersionResponse version(@PathVariable UUID id,
                                           @PathVariable @Positive long version) {
        return DocumentVersionResponse.from(queries.version(new DocumentId(id), version));
    }

    @GetMapping
    @Operation(summary = "Search documents using an exclusive row window")
    @ApiResponse(responseCode = "200", description = "Filtered total and summary rows",
            content = @Content(schema = @Schema(implementation = DocumentListResponse.class)))
    public DocumentListResponse search(
            @RequestParam(defaultValue = "0") int startRow,
            @RequestParam(defaultValue = "100") int endRow,
            @Parameter(description = "Allowed fields: title, updatedAt, status, version; direction: asc or desc")
            @RequestParam(defaultValue = "updatedAt:desc") String sort,
            @Parameter(schema = @Schema(allowableValues = {
                    "Draft", "Submitted", "InReview", "Approved", "Rejected", "Published", "Archived"}))
            @RequestParam(name = "filter.status", required = false) String status,
            @RequestParam(defaultValue = "") String q) {
        var filter = status == null ? Optional.<DocumentStatus>empty() : Optional.of(DocumentStatus.fromName(status));
        return DocumentListResponse.from(queries.search(new DocumentSearch(startRow, endRow, parseSort(sort), filter, q)));
    }

    @PutMapping(value = "/{id}/content", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the content of a draft or rejected document")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse update(@PathVariable UUID id,
                                   @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                   @Valid @RequestBody UpdateDocumentRequest request) {
        return DocumentResponse.from(commands.update(new UpdateDocumentCommand(new DocumentId(id), request.expectedVersion(),
                request.title(), request.sections().stream().map(SectionRequest::toDomain).toList(), new Actor(author))));
    }

    @PostMapping(value = "/{id}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit a completed draft or rejected document")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse submit(@PathVariable UUID id,
                                   @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                   @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.submit(change(id, request, author)));
    }

    @PostMapping(value = "/{id}/start-review", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start reviewing a submitted document")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse startReview(@PathVariable UUID id,
                                        @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                        @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.startReview(change(id, request, author)));
    }

    @PostMapping(value = "/{id}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Approve a document in review")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse approve(@PathVariable UUID id,
                                    @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                    @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.approve(change(id, request, author)));
    }

    @PostMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reject a document in review")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse reject(@PathVariable UUID id,
                                   @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                   @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.reject(change(id, request, author)));
    }

    @PostMapping(value = "/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publish an approved document")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse publish(@PathVariable UUID id,
                                    @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                    @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.publish(change(id, request, author)));
    }

    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Archive a published document")
    @ApiResponse(responseCode = "200", description = "Document with its new version",
            content = @Content(schema = @Schema(implementation = DocumentResponse.class)))
    public DocumentResponse archive(@PathVariable UUID id,
                                    @RequestHeader("X-User") @NotBlank @Size(max = 100) String author,
                                    @Valid @RequestBody ChangeDocumentStatusRequest request) {
        return DocumentResponse.from(commands.archive(change(id, request, author)));
    }

    private static ChangeDocumentStatusCommand change(UUID id, ChangeDocumentStatusRequest request, String author) {
        return new ChangeDocumentStatusCommand(new DocumentId(id), request.expectedVersion(), new Actor(author));
    }

    private static DocumentSort parseSort(String sort) {
        var parts = sort.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Sort must have the form field:asc or field:desc");
        }
        var field = switch (parts[0]) {
            case "title" -> DocumentSort.Field.TITLE;
            case "updatedAt" -> DocumentSort.Field.UPDATED_AT;
            case "status" -> DocumentSort.Field.STATUS;
            case "version" -> DocumentSort.Field.VERSION;
            default -> throw new IllegalArgumentException("Unsupported sort field");
        };
        var direction = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "asc" -> DocumentSort.Direction.ASC;
            case "desc" -> DocumentSort.Direction.DESC;
            default -> throw new IllegalArgumentException("Unsupported sort direction");
        };
        return new DocumentSort(field, direction);
    }
}
