package fr.walid.acd.contribution.application.exception;

import fr.walid.acd.contribution.application.importing.SourceRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExceptionsTest {

    @Test
    void aSourceReferenceCollisionCarriesTheReference() {
        var reference = new SourceRef("annuaire", "DOC-1");

        var exception = new SourceRefAlreadyUsedException(reference);

        assertThat(exception.sourceRef()).isEqualTo(reference);
        assertThat(exception).hasMessage("Source reference already imported: annuaire/DOC-1");
    }

    @Test
    void aRejectedBatchCarriesItsCode() {
        var exception = new ImportBatchRejectedException("UNKNOWN_SOURCE", "Source inconnue : annuaire");

        assertThat(exception.code()).isEqualTo("UNKNOWN_SOURCE");
        assertThat(exception).hasMessage("Source inconnue : annuaire");
    }
}
