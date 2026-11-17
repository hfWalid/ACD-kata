package fr.walid.acd.architecturefixtures.entrypoint;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Deliberate misplaced entrypoint; excluded from production imports and component scanning. */
@SpringBootApplication
public class BadEntrypoint {}
