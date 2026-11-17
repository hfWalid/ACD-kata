package fr.walid.acd.contribution.domain;

public record Actor(String value) {
    public Actor {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }
        value = value.strip();
        if (value.length() > 100) {
            throw new IllegalArgumentException("Author must not exceed 100 characters");
        }
    }
}
