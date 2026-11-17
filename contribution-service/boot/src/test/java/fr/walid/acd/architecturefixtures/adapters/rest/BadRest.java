package fr.walid.acd.architecturefixtures.adapters.rest;

import fr.walid.acd.architecturefixtures.adapters.persistence.BadPersistence;

/** Deliberate adapter-to-adapter dependency. */
public final class BadRest {
    BadPersistence persistence;
}
