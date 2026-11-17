package fr.walid.acd.contribution.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "fr.walid.acd.contribution")
@EntityScan("fr.walid.acd.contribution.adapters.persistence")
public class ContributionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContributionApplication.class, args);
    }
}
