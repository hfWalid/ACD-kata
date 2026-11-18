package fr.walid.acd.architecturefixtures.adapters.persistence;

import fr.walid.acd.contribution.boot.ContributionApplication;

/** An adapter depending on the composition root, which must stay a leaf. */
public final class BadBootDependency {
    private final Class<?> entrypoint = ContributionApplication.class;
}
