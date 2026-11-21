package fr.walid.acd.review.adapter;

import fr.walid.acd.review.application.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the lifecycle topic. Failures are retried by the container, then published to the
 * dead-letter topic configured in {@link ReviewKafkaConfiguration}.
 */
@Component
final class DocumentLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentLifecycleListener.class);

    private final EventEnvelopeJson json;
    private final ReviewService reviews;

    DocumentLifecycleListener(EventEnvelopeJson json, ReviewService reviews) {
        this.json = json;
        this.reviews = reviews;
    }

    @KafkaListener(topics = "${acd.events.topic:acd.document.lifecycle.v1}",
            groupId = "${spring.kafka.consumer.group-id:review-service}")
    void onDocumentLifecycleEvent(String payload) {
        var event = json.read(payload);
        var applied = reviews.handle(event);
        if (applied) {
            LOGGER.info("Applied {} for document {} version {}", event.action(), event.documentId(),
                    event.documentVersion());
        } else {
            LOGGER.debug("Ignored {} for document {} version {}", event.action(), event.documentId(),
                    event.documentVersion());
        }
    }
}
