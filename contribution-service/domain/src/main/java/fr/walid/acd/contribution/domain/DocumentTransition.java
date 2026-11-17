package fr.walid.acd.contribution.domain;

final class DocumentTransition {
    private DocumentTransition() {}

    static DocumentStatus next(DocumentStatus status, DocumentAction action) {
        return switch (status) {
            case DocumentStatus.Draft() -> switch (action) {
                case SUBMITTED -> new DocumentStatus.Submitted();
                case CONTENT_UPDATED -> status;
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.Submitted() -> switch (action) {
                case REVIEW_STARTED -> new DocumentStatus.InReview();
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.InReview() -> switch (action) {
                case APPROVED -> new DocumentStatus.Approved();
                case REJECTED -> new DocumentStatus.Rejected();
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.Approved() -> switch (action) {
                case PUBLISHED -> new DocumentStatus.Published();
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.Rejected() -> switch (action) {
                case SUBMITTED -> new DocumentStatus.Submitted();
                case CONTENT_UPDATED -> status;
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.Published() -> switch (action) {
                case ARCHIVED -> new DocumentStatus.Archived();
                default -> throw forbidden(status, action);
            };
            case DocumentStatus.Archived() -> throw forbidden(status, action);
        };
    }

    private static DomainException forbidden(DocumentStatus status, DocumentAction action) {
        return new DomainException("INVALID_TRANSITION", "Cannot " + action + " a document in " + status.name());
    }
}
