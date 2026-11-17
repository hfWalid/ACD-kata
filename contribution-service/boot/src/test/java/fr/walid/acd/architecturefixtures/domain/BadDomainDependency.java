package fr.walid.acd.architecturefixtures.domain;

import fr.walid.acd.contribution.boot.ContributionApplication;

/** Deliberate dependency from a domain fixture to the composition root. */
public final class BadDomainDependency {
    ContributionApplication compositionRoot;
}
