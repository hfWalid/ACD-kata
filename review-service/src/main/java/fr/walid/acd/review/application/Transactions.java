package fr.walid.acd.review.application;

import java.util.function.Supplier;

/** One transaction for the inbox row and the effect it describes. */
public interface Transactions {

    <T> T inTransaction(Supplier<T> work);
}
