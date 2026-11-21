package fr.walid.acd.architecturefixtures.adapters.importing;

import fr.walid.acd.architecturefixtures.adapters.rest.BadRest;

/** Deliberate adapter-to-adapter dependency, from the import adapter to the transport one. */
public final class BadImport {
    BadRest rest;
}
