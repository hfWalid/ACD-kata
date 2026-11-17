package fr.walid.acd.contribution.adapters.rest;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentSort;
import fr.walid.acd.contribution.application.query.DocumentSummary;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.DomainException;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(controllers = DocumentController.class, properties = {
        "spring.jackson.deserialization.fail-on-unknown-properties=true",
        "spring.jackson.deserialization.accept-float-as-int=false",
        "spring.jackson.mapper.allow-coercion-of-scalars=false"
})
@ContextConfiguration(classes = {DocumentController.class, ApiExceptionHandler.class})
class DocumentControllerTest {
    private static final DocumentId ID = new DocumentId(UUID.fromString("3bc50806-f270-4d57-8210-b3f00be48a0b"));
    private static final Actor AUTHOR = new Actor("walid");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final String BASE = "/api/documents";
    private static final String CREATE = """
            {"templateRef":"NOTE","title":"Note technique","sections":[{"title":"Objet","content":"Contenu"}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentCommands commands;

    @MockitoBean
    private DocumentQueries queries;

    private Document draft;

    @BeforeEach
    void createDocumentFixture() {
        draft = Document.create(ID, new TemplateRef("NOTE"), "Note technique",
                List.of(new Section("Objet", "Contenu")), AUTHOR, NOW);
    }

    @Test
    void createsADraftAndReturnsItsLocationAndAudit() throws Exception {
        when(commands.create(any())).thenReturn(draft);

        mvc.perform(post(BASE).header("X-User", "walid").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + ID.value()))
                .andExpect(jsonPath("$.id").value(ID.value().toString()))
                .andExpect(jsonPath("$.templateRef").value("NOTE"))
                .andExpect(jsonPath("$.status").value("Draft"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sections[0].content").value("Contenu"))
                .andExpect(jsonPath("$.auditTrail[0].action").value("CREATED"))
                .andExpect(jsonPath("$.auditTrail[0].author").value("walid"))
                .andExpect(jsonPath("$.auditTrail[0].occurredAt").value(NOW.toString()));

        verify(commands).create(new CreateDocumentCommand(new TemplateRef("NOTE"), "Note technique",
                List.of(new Section("Objet", "Contenu")), AUTHOR));
    }

    @Test
    void allowsAnEmptyDraft() throws Exception {
        when(commands.create(any())).thenReturn(Document.create(ID, new TemplateRef("NOTE"), "Brouillon",
                List.of(), AUTHOR, NOW));

        mvc.perform(post(BASE).header("X-User", "walid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateRef\":\"NOTE\",\"title\":\"Brouillon\",\"sections\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections").isEmpty());
    }

    @Test
    void readsADocumentWithoutRequiringAnAuthorHeader() throws Exception {
        when(queries.get(ID)).thenReturn(draft);

        mvc.perform(get(BASE + "/" + ID.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Note technique"));
    }

    @Test
    void updatesContentWithAnExplicitExpectedVersion() throws Exception {
        when(commands.update(any())).thenReturn(draft.updateContent("Titre corrigé", List.of(), AUTHOR, NOW));

        mvc.perform(put(BASE + "/" + ID.value() + "/content").header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"title\":\"Titre corrigé\",\"sections\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre corrigé"))
                .andExpect(jsonPath("$.version").value(2));

        verify(commands).update(new UpdateDocumentCommand(ID, 1, "Titre corrigé", List.of(), AUTHOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"submit", "start-review", "approve", "reject", "publish", "archive"})
    void dispatchesEveryTransitionToItsUseCase(String action) throws Exception {
        var submitted = draft.submit(AUTHOR, NOW);
        var inReview = submitted.startReview(AUTHOR, NOW);
        var approved = inReview.approve(AUTHOR, NOW);
        var published = approved.publish(AUTHOR, NOW);
        Document result = switch (action) {
            case "submit" -> submitted;
            case "start-review" -> inReview;
            case "approve" -> approved;
            case "reject" -> inReview.reject(AUTHOR, NOW);
            case "publish" -> published;
            case "archive" -> published.archive(AUTHOR, NOW);
            default -> throw new AssertionError(action);
        };
        switch (action) {
            case "submit" -> when(commands.submit(any())).thenReturn(result);
            case "start-review" -> when(commands.startReview(any())).thenReturn(result);
            case "approve" -> when(commands.approve(any())).thenReturn(result);
            case "reject" -> when(commands.reject(any())).thenReturn(result);
            case "publish" -> when(commands.publish(any())).thenReturn(result);
            case "archive" -> when(commands.archive(any())).thenReturn(result);
            default -> throw new AssertionError(action);
        }

        mvc.perform(post(BASE + "/" + ID.value() + "/" + action).header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(result.status().name()))
                .andExpect(jsonPath("$.version").value(result.version()));

        var command = new ChangeDocumentStatusCommand(ID, 1, AUTHOR);
        switch (action) {
            case "submit" -> verify(commands).submit(command);
            case "start-review" -> verify(commands).startReview(command);
            case "approve" -> verify(commands).approve(command);
            case "reject" -> verify(commands).reject(command);
            case "publish" -> verify(commands).publish(command);
            case "archive" -> verify(commands).archive(command);
            default -> throw new AssertionError(action);
        }
    }

    @Test
    void translatesTheGridWindowAndFiltersToATypedQuery() throws Exception {
        var search = new DocumentSearch(3, 8,
                new DocumentSort(DocumentSort.Field.TITLE, DocumentSort.Direction.ASC),
                Optional.of(new DocumentStatus.Draft()), "note");
        when(queries.search(search)).thenReturn(new DocumentPage(12,
                List.of(new DocumentSummary(ID, draft.title(), draft.status(), draft.version(), draft.updatedAt()))));

        mvc.perform(get(BASE).param("startRow", "3").param("endRow", "8").param("sort", "title:asc")
                        .param("filter.status", "Draft").param("q", "note"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(12))
                .andExpect(jsonPath("$.rows[0].status").value("Draft"));

        verify(queries).search(search);
    }

    @Test
    void suppliesTheDefaultGridWindowAndSort() throws Exception {
        var search = new DocumentSearch(0, 100,
                new DocumentSort(DocumentSort.Field.UPDATED_AT, DocumentSort.Direction.DESC), Optional.empty(), "");
        when(queries.search(search)).thenReturn(new DocumentPage(0, List.of()));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(0))
                .andExpect(jsonPath("$.rows").isEmpty());

        verify(queries).search(search);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidAuthorHeaders(String author) throws Exception {
        mvc.perform(post(BASE).header("X-User", author).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commands);
    }

    @Test
    void rejectsAMissingAuthorHeader() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"templateRef\":\"NOTE\",\"title\":\" \",\"sections\":[]}",
            "{\"templateRef\":\"NOTE\",\"title\":\"Titre\",\"sections\":null}",
            "{\"templateRef\":\"NOTE\",\"title\":\"Titre\",\"sections\":[null]}",
            "{\"templateRef\":\"NOTE\",\"title\":\"Titre\",\"sections\":[{\"title\":\"Objet\",\"content\":null}]}",
            "{\"templateRef\":\"NOTE\",\"title\":\"Titre\",\"sections\":[],\"status\":\"Approved\"}"
    })
    void rejectsInvalidCreationPayloads(String payload) throws Exception {
        mvc.perform(post(BASE).header("X-User", "walid").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":0}", "{\"expectedVersion\":-1}",
            "{\"expectedVersion\":\"invalid\"}", "{\"expectedVersion\":1.5}", "{\"expectedVersion\":\"1\"}"})
    void requiresAPositiveExpectedVersion(String payload) throws Exception {
        mvc.perform(post(BASE + "/" + ID.value() + "/submit").header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\"Titre\",\"sections\":[]}",
            "{\"expectedVersion\":1,\"title\":\" \",\"sections\":[]}"})
    void validatesContentReplacements(String payload) throws Exception {
        mvc.perform(put(BASE + "/" + ID.value() + "/content").header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commands);
    }

    @Test
    void rejectsAnInvalidDocumentIdentifier() throws Exception {
        mvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"startRow=-1", "endRow=0", "endRow=501", "startRow=1000001", "startRow=abc",
            "sort=unknown:asc", "sort=title:sideways", "sort=title", "filter.status=invalid"})
    void rejectsInvalidGridQueries(String query) throws Exception {
        var parts = query.split("=", 2);
        mvc.perform(get(BASE).param(parts[0], parts[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(queries);
    }

    @Test
    void reportsAMissingDocumentAsProblemDetail() throws Exception {
        when(queries.get(ID)).thenThrow(new DocumentNotFoundException(ID));

        mvc.perform(get(BASE + "/" + ID.value()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void reportsAnOptimisticConflictWithoutLeakingInfrastructureDetails() throws Exception {
        when(commands.submit(any())).thenThrow(new DocumentConflictException(ID));

        mvc.perform(post(BASE + "/" + ID.value() + "/submit").header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void reportsTheDomainRuleThatPreventsATransition() throws Exception {
        when(commands.publish(any())).thenThrow(new DomainException("INVALID_TRANSITION", "Publishing requires approval"));

        mvc.perform(post(BASE + "/" + ID.value() + "/publish").header("X-User", "walid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void hidesUnexpectedFailureDetailsBehindAProblemResponse() throws Exception {
        when(queries.get(ID)).thenThrow(new IllegalStateException("internal-driver-diagnostic"));

        mvc.perform(get(BASE + "/" + ID.value()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("internal-driver-diagnostic"))));
    }
}
