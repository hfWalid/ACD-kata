package fr.walid.acd.architecturefixtures.adapters.persistence;

import fr.walid.acd.architecturefixtures.adapters.messaging.BadMessaging;

/** Deliberate adapter-to-adapter dependency. */
public final class BadPersistence {
    BadMessaging messaging;
}
