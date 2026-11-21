package fr.walid.acd.contribution.application.port.out;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;

/**
 * Publishes an event to the outside world. Implementations must block until the broker
 * acknowledged it: a returned call is a publication the relay may mark as done.
 */
public interface EventPublisher {

    void publish(DocumentLifecycleEvent event);
}
