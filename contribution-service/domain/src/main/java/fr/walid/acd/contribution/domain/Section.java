package fr.walid.acd.contribution.domain;

public record Section(String title, String content) {
    public Section {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Section title is required");
        }
        title = title.strip();
        if (title.length() > 200) {
            throw new IllegalArgumentException("Section title must not exceed 200 characters");
        }
        if (content == null || content.length() > 100_000) {
            throw new IllegalArgumentException("Section content is required and must not exceed 100000 characters");
        }
    }
}
