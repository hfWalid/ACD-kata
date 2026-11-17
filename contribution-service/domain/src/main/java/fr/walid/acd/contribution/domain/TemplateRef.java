package fr.walid.acd.contribution.domain;

public record TemplateRef(String value) {
    public TemplateRef {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Template reference is required");
        }
        value = value.strip();
        if (value.length() > 100) {
            throw new IllegalArgumentException("Template reference must not exceed 100 characters");
        }
    }
}
