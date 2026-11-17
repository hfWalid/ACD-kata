package fr.walid.acd.contribution.domain;

public sealed interface DocumentStatus {
    record Draft() implements DocumentStatus {}
    record Submitted() implements DocumentStatus {}
    record InReview() implements DocumentStatus {}
    record Approved() implements DocumentStatus {}
    record Rejected() implements DocumentStatus {}
    record Published() implements DocumentStatus {}
    record Archived() implements DocumentStatus {}

    default String name() {
        return getClass().getSimpleName();
    }

    static DocumentStatus fromName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Document status is required");
        }
        return switch (name) {
            case "Draft" -> new Draft();
            case "Submitted" -> new Submitted();
            case "InReview" -> new InReview();
            case "Approved" -> new Approved();
            case "Rejected" -> new Rejected();
            case "Published" -> new Published();
            case "Archived" -> new Archived();
            default -> throw new IllegalArgumentException("Unknown document status: " + name);
        };
    }
}
