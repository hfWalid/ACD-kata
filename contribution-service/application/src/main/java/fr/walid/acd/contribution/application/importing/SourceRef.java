package fr.walid.acd.contribution.application.importing;

/**
 * Where an imported document comes from: a named import source and the reference the file gives it.
 * The pair is unique, so replaying the same file never creates a second document.
 */
public record SourceRef(String source, String value) {

    private static final int MAX_VALUE_LENGTH = 200;

    public SourceRef {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Import source is required");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Import reference is required");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Import reference must not exceed 200 characters");
        }
    }
}
