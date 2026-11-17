package fr.walid.acd.architecturefixtures.application;

import fr.walid.acd.contribution.boot.ContributionApplication;

/** Deliberate reverse dependency for the architecture rule's counterexample. */
public final class BadApplication {
    ContributionApplication compositionRoot;
}
