package fr.walid.acd.architecturefixtures.adapters.rest;

import fr.walid.acd.contribution.domain.Document;

/** A transport adapter exposing the aggregate outside the application boundary. */
public final class BadRestAggregate {
    public Document document() {
        return null;
    }
}
