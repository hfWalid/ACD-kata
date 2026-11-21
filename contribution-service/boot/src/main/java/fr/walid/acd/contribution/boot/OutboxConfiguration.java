package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.EventPublisher;
import fr.walid.acd.contribution.application.service.OutboxPurge;
import fr.walid.acd.contribution.application.service.OutboxRelay;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OutboxRelayProperties.class, OutboxPurgeProperties.class})
@EnableScheduling
class OutboxConfiguration {

    @Bean
    OutboxRelay outboxRelay(EventOutbox outbox, EventPublisher publisher, Clock clock,
                            OutboxRelayProperties properties) {
        return new OutboxRelay(outbox, publisher, clock, properties.lease(), properties.baseBackoff(),
                properties.maxBackoff());
    }

    @Bean
    OutboxPurge outboxPurge(EventOutbox outbox, Clock clock, OutboxPurgeProperties properties) {
        return new OutboxPurge(outbox, clock, properties.retention());
    }

    /**
     * Two gauges, scraped on demand: a growing {@code pending} means the broker is late, a non-zero
     * {@code failed} means at least one publication was refused and is being retried.
     */
    @Bean
    MeterBinder outboxGauges(EventOutbox outbox) {
        return registry -> bind(registry, outbox);
    }

    private static void bind(MeterRegistry registry, EventOutbox outbox) {
        Gauge.builder("acd.outbox.pending", outbox, EventOutbox::countPending)
                .description("Events written and not published yet")
                .register(registry);
        Gauge.builder("acd.outbox.failed", outbox, EventOutbox::countFailed)
                .description("Unpublished events after at least one failed publication")
                .register(registry);
    }
}
