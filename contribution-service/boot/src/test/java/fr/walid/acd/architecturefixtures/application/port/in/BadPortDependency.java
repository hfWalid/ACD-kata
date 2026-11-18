package fr.walid.acd.architecturefixtures.application.port.in;

import fr.walid.acd.architecturefixtures.application.service.BadService;

/** An inbound port depending on an implementation instead of the reverse. */
public interface BadPortDependency {
    BadService service();
}
