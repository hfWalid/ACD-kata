package fr.walid.acd.contribution.application.importing;

import fr.walid.acd.contribution.domain.Actor;

import java.util.Objects;

/**
 * Who runs the import, how far each document goes, and whether anything is written.
 *
 * @param actor    the author declared on the created documents and their transitions
 * @param approver the actor recorded on the approval; the caller defaults it to the author
 * @param mode     the status reached by each imported document
 * @param dryRun   when true, the workflow is simulated and nothing is written
 */
public record ImportOptions(Actor actor, Actor approver, ImportMode mode, boolean dryRun) {

    public ImportOptions {
        Objects.requireNonNull(actor, "Import actor is required");
        Objects.requireNonNull(approver, "Import approver is required");
        Objects.requireNonNull(mode, "Import mode is required");
    }
}
