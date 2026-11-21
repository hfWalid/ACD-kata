package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.adapters.importing.JoltImportSource;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.DocumentImport;
import fr.walid.acd.contribution.application.port.out.DocumentSourceIndex;
import fr.walid.acd.contribution.application.port.out.ImportSource;
import fr.walid.acd.contribution.application.port.out.Transactions;
import fr.walid.acd.contribution.application.service.DocumentImportService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImportProperties.class)
class ImportConfiguration {

    /** The mapping is a file, so the application reads it from a directory rather than the classpath. */
    @Bean
    ImportSource importSource(ImportProperties properties) {
        return new JoltImportSource(properties.specsDirectory(), properties.maxRows());
    }

    @Bean
    DocumentImport documentImport(DocumentCommands commands, DocumentSourceIndex sourceIndex,
                                  Transactions transactions, Clock clock) {
        return new DocumentImportService(commands, sourceIndex, transactions, clock, UUID::randomUUID);
    }
}
