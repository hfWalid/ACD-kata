package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.Transactions;
import fr.walid.acd.contribution.application.service.DocumentCommandService;
import fr.walid.acd.contribution.application.service.DocumentQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
class DocumentConfiguration {

    @Bean
    Clock documentClock() {
        // PostgreSQL timestamps retain microseconds; returned and reloaded audit times agree.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }

    @Bean
    DocumentCommands documentCommands(DocumentRepository repository, EventOutbox outbox, Transactions transactions,
                                      Clock clock) {
        return new DocumentCommandService(repository, outbox, transactions, clock, UUID::randomUUID);
    }

    @Bean
    DocumentQueries documentQueries(DocumentRepository repository) {
        return new DocumentQueryService(repository);
    }
}
