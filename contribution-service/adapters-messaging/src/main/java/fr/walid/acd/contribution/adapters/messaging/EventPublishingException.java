package fr.walid.acd.contribution.adapters.messaging;

/**
 * The broker did not accept the event. The outbox keeps the row and retries it, so a failure here
 * delays an event instead of losing it.
 */
public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
