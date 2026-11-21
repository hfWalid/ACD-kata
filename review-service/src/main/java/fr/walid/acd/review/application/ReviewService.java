package fr.walid.acd.review.application;

import java.util.Objects;

/**
 * Turns document lifecycle events into review tasks. Submission opens a task, a decision closes it,
 * every other action only records that the document moved. Processing is at-least-once, so the same
 * event may arrive twice: the inbox and the version guard make the repetition a no-op.
 */
public final class ReviewService {
    private static final String SUBMITTED = "SUBMITTED";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final ReviewTasks tasks;
    private final Transactions transactions;

    public ReviewService(ReviewTasks tasks, Transactions transactions) {
        this.tasks = Objects.requireNonNull(tasks, "Tasks are required");
        this.transactions = Objects.requireNonNull(transactions, "Transactions are required");
    }

    /** @return {@code true} when the event changed something, {@code false} when it was ignored. */
    public boolean handle(DocumentLifecycleEvent event) {
        return transactions.inTransaction(() -> {
            if (!tasks.remember(event)) {
                return false;
            }
            if (!tasks.advanceTo(event)) {
                return false;
            }
            switch (event.action()) {
                case SUBMITTED -> tasks.openReview(event);
                case APPROVED -> tasks.decide(event, APPROVED);
                case REJECTED -> tasks.decide(event, REJECTED);
                default -> {
                    // The document moved without opening or closing a review.
                }
            }
            return true;
        });
    }
}
