package fr.walid.acd.review.adapter;

/**
 * The payload could not be read as the event version this service understands. It is not retryable:
 * the message goes to the dead-letter topic so it cannot block the partition.
 */
public class EventContractException extends RuntimeException {

    public EventContractException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventContractException(String message) {
        super(message);
    }
}
