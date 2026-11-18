package fr.walid.acd.architecturefixtures.adapters.rest;

import fr.walid.acd.contribution.application.service.DocumentQueryService;

/** A transport adapter reaching a concrete use case instead of its inbound port. */
public final class BadUseCaseDependency {
    private final DocumentQueryService service = null;
}
