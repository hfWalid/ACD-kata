package fr.walid.acd.contribution.boot;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import fr.walid.acd.architecturefixtures.adapters.messaging.BadMessaging;
import fr.walid.acd.architecturefixtures.adapters.persistence.BadPersistence;
import fr.walid.acd.architecturefixtures.adapters.rest.BadRest;
import fr.walid.acd.architecturefixtures.application.BadApplication;
import fr.walid.acd.architecturefixtures.domain.BadDomainDependency;
import fr.walid.acd.architecturefixtures.domain.BadDomainFramework;
import fr.walid.acd.architecturefixtures.entrypoint.BadEntrypoint;
import fr.walid.acd.architecturefixtures.foreign.BadPortImplementation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureSmokeTest {
    private static final String ROOT = "fr.walid.acd.contribution";
    private static final String DOMAIN = ROOT + ".domain";
    private static final String APPLICATION = ROOT + ".application";
    private static final String FIXTURES = "fr.walid.acd.architecturefixtures";

    @Test
    void productionCodeRespectsTheHexagonalBoundaries() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        frameworkFreeDomain(DOMAIN).check(production);
        pureDomain(DOMAIN).check(production);
        applicationDependencies(APPLICATION).check(production);
        independentRestAdapter(ROOT + ".adapters").check(production);
        independentPersistenceAdapter(ROOT + ".adapters").check(production);
        independentMessagingAdapter(ROOT + ".adapters").check(production);
        portImplementations().check(production);
        bootEntrypoints(ROOT + ".boot").check(production);
    }

    @Test
    void rejectsAFrameworkDependencyInTheDomain() {
        rejects(frameworkFreeDomain(FIXTURES + ".domain"), BadDomainFramework.class);
    }

    @Test
    void rejectsANonJdkDependencyOutsideTheDomain() {
        rejects(pureDomain(FIXTURES + ".domain"), BadDomainDependency.class);
    }

    @Test
    void rejectsAnApplicationDependencyOnTheCompositionRoot() {
        rejects(applicationDependencies(FIXTURES + ".application"), BadApplication.class);
    }

    @Test
    void rejectsDependenciesBetweenEveryPairOfAdapterBoundaries() {
        rejects(independentRestAdapter(FIXTURES + ".adapters"), BadRest.class);
        rejects(independentPersistenceAdapter(FIXTURES + ".adapters"), BadPersistence.class);
        rejects(independentMessagingAdapter(FIXTURES + ".adapters"), BadMessaging.class);
    }

    @Test
    void rejectsAPortImplementationOutsideApplicationAndAdapters() {
        rejects(portImplementations(), BadPortImplementation.class);
    }

    @Test
    void rejectsASpringBootEntrypointOutsideTheCompositionRoot() {
        rejects(bootEntrypoints(ROOT + ".boot"), BadEntrypoint.class);
    }

    private static ArchRule frameworkFreeDomain(String domainPackage) {
        return noClasses().that().resideInAPackage(domainPackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..");
    }

    private static ArchRule pureDomain(String domainPackage) {
        return classes().that().resideInAPackage(domainPackage + "..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(domainPackage + "..", "java..");
    }

    private static ArchRule applicationDependencies(String applicationPackage) {
        return classes().that().resideInAPackage(applicationPackage + "..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(applicationPackage + "..", DOMAIN + "..", "java..");
    }

    private static ArchRule independentRestAdapter(String adaptersPackage) {
        return noClasses().that().resideInAPackage(adaptersPackage + ".rest..")
                .should().dependOnClassesThat().resideInAnyPackage(adaptersPackage + ".persistence..", adaptersPackage + ".messaging..");
    }

    private static ArchRule independentPersistenceAdapter(String adaptersPackage) {
        return noClasses().that().resideInAPackage(adaptersPackage + ".persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(adaptersPackage + ".rest..", adaptersPackage + ".messaging..");
    }

    private static ArchRule independentMessagingAdapter(String adaptersPackage) {
        // The messaging adapter remains intentionally empty until S8.
        return noClasses().that().resideInAPackage(adaptersPackage + ".messaging..")
                .should().dependOnClassesThat().resideInAnyPackage(adaptersPackage + ".rest..", adaptersPackage + ".persistence..")
                .allowEmptyShould(true);
    }

    private static ArchRule portImplementations() {
        var implementsApplicationPort = new DescribedPredicate<JavaClass>("implement an application port") {
            @Override
            public boolean test(JavaClass type) {
                return !type.isInterface() && type.getAllRawInterfaces().stream()
                        .anyMatch(contract -> contract.getPackageName().startsWith(APPLICATION + ".port."));
            }
        };
        return classes().that(implementsApplicationPort)
                .should().resideInAnyPackage(APPLICATION + "..", ROOT + ".adapters..");
    }

    private static ArchRule bootEntrypoints(String bootPackage) {
        return classes().that().areAnnotatedWith(SpringBootApplication.class)
                .should().resideInAPackage(bootPackage + "..");
    }

    private static void rejects(ArchRule rule, Class<?> fixture) {
        var invalidClasses = new ClassFileImporter().importClasses(fixture);
        assertThatThrownBy(() -> rule.check(invalidClasses))
                .isInstanceOf(AssertionError.class).hasMessageContaining(fixture.getSimpleName());
    }
}
