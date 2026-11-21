package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.EventPublisher;
import fr.walid.acd.contribution.application.service.OutboxRelay;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxRelayProperties.class)
@EnableScheduling
class OutboxConfiguration {

    @Bean
    OutboxRelay outboxRelay(EventOutbox outbox, EventPublisher publisher, Clock clock,
                            OutboxRelayProperties properties) {
        return new OutboxRelay(outbox, publisher, clock, properties.lease(), properties.baseBackoff(),
                properties.maxBackoff());
    }
}
