package fr.walid.acd.contribution.application.port.out;

import java.util.function.Supplier;

/**
 * Runs work inside one transaction, so the state change and its outbox event commit together.
 * The application keeps deciding what is atomic; only the boundary is delegated.
 */
public interface Transactions {

    <T> T inTransaction(Supplier<T> work);
}
