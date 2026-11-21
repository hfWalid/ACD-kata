package fr.walid.acd.contribution.adapters.rest.importing;

import fr.walid.acd.contribution.adapters.rest.problem.ApiExceptionHandler;
import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;
import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.ImportMode;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.ImportReport;
import fr.walid.acd.contribution.application.importing.RowOutcome;
import fr.walid.acd.contribution.application.port.in.DocumentImport;
import fr.walid.acd.contribution.application.port.out.ImportSource;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportController.class, properties = {
        "spring.mvc.problemdetails.enabled=true",
        "spring.http.multipart.max-file-size=1MB"
})
@ContextConfiguration(classes = {ImportController.class, ApiExceptionHandler.class})
class ImportControllerTest {
    private static final DocumentId DOCUMENT_ID = new DocumentId(UUID.fromString("018f0000-0000-7000-8000-000000000001"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentImport importer;

    @MockitoBean
    private ImportSource importSource;

    @Test
    void importsAFileAndReturnsTheReport() throws Exception {
        when(importSource.read(any(), any(), any())).thenReturn(new ImportBatch("annuaire", List.of(), List.of()));
        when(importer.importDocuments(any(), any())).thenReturn(new ImportReport("annuaire", ImportMode.APPROVED, false,
                List.of(RowOutcome.rejected(2, "DOC-1", "EMPTY_DOCUMENT", "A submitted document requires non-empty sections"),
                        new RowOutcome(1, "DOC-0", fr.walid.acd.contribution.application.importing.RowStatus.IMPORTED,
                                DOCUMENT_ID, 4, "Approved", null, null))));

        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "annuaire.csv", "text/csv", "reference;titre".getBytes()))
                        .param("source", "annuaire")
                        .param("mode", "approved")
                        .header("X-User", "walid")
                        .header("X-Approver", "relecteur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("annuaire"))
                .andExpect(jsonPath("$.mode").value("APPROVED"))
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.rows[0].line").value(1))
                .andExpect(jsonPath("$.rows[0].documentId").value(DOCUMENT_ID.value().toString()))
                .andExpect(jsonPath("$.rows[1].code").value("EMPTY_DOCUMENT"));

        var options = ArgumentCaptor.forClass(ImportOptions.class);
        verify(importer).importDocuments(any(), options.capture());
        assertThat(options.getValue().actor()).isEqualTo(new Actor("walid"));
        assertThat(options.getValue().approver()).isEqualTo(new Actor("relecteur"));
        assertThat(options.getValue().mode()).isEqualTo(ImportMode.APPROVED);
    }

    @Test
    void defaultsTheApproverToTheAuthorAndTheModeToADraft() throws Exception {
        when(importSource.read(any(), any(), any())).thenReturn(new ImportBatch("annuaire", List.of(), List.of()));
        when(importer.importDocuments(any(), any())).thenReturn(new ImportReport("annuaire", ImportMode.DRAFT, true,
                List.of()));

        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "annuaire.csv", "text/csv", new byte[0]))
                        .param("source", "annuaire")
                        .param("dryRun", "true")
                        .header("X-User", "walid"))
                .andExpect(status().isOk());

        var options = ArgumentCaptor.forClass(ImportOptions.class);
        verify(importer).importDocuments(any(), options.capture());
        assertThat(options.getValue().approver()).isEqualTo(new Actor("walid"));
        assertThat(options.getValue().mode()).isEqualTo(ImportMode.DRAFT);
        assertThat(options.getValue().dryRun()).isTrue();
    }

    @Test
    void refusesAnUnknownModeWithoutReadingTheFile() throws Exception {
        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "annuaire.csv", "text/csv", new byte[0]))
                        .param("source", "annuaire")
                        .param("mode", "publie")
                        .header("X-User", "walid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_MODE"))
                .andExpect(jsonPath("$.detail").value("Mode d'import inconnu : publie"));

        verifyNoInteractions(importSource);
    }

    @Test
    void refusesAnUnknownSourceWithTheCodeTheReaderChose() throws Exception {
        when(importSource.read(any(), any(), any()))
                .thenThrow(new ImportBatchRejectedException("UNKNOWN_SOURCE", "Source d'import inconnue : inconnue"));

        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "annuaire.csv", "text/csv", new byte[0]))
                        .param("source", "inconnue")
                        .header("X-User", "walid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_SOURCE"));
    }

    @Test
    void refusesARequestWithoutAnAuthor() throws Exception {
        mvc.perform(multipart("/api/imports")
                        .file(new MockMultipartFile("file", "annuaire.csv", MediaType.TEXT_PLAIN_VALUE, new byte[0]))
                        .param("source", "annuaire"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(importSource, importer);
    }
}
