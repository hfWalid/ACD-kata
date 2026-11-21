package fr.walid.acd.contribution.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "fr.walid.acd.contribution")
@EntityScan("fr.walid.acd.contribution.adapters.persistence")
public class ContributionApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(ContributionApplication.class, args);
        // A command line import must end the process with its report code; a web run keeps serving.
        if (context.getBeanProvider(ImportRunner.class).getIfAvailable() != null) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
