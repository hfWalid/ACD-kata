package fr.walid.acd.review.application;

/**
 * Side effects of an event, and the two guards that make a repetition harmless.
 */
public interface ReviewTasks {

    /** Records the event id; {@code false} when it was already processed. */
    boolean remember(DocumentLifecycleEvent event);

    /**
     * Records that a document is known up to {@code version}; {@code false} when the event is
     * older than what was already applied, so an out-of-order replay cannot reopen old work.
     */
    boolean advanceTo(DocumentLifecycleEvent event);

    /** Opens the task of the submitted version; a resubmission at a higher version opens a new one. */
    void openReview(DocumentLifecycleEvent event);

    /** Closes the task still pending for the document: a decision arrives after submission. */
    void decide(DocumentLifecycleEvent event, String decision);
}
