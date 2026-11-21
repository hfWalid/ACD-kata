package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.port.out.Transactions;

import java.util.function.Supplier;

final class FakeTransactions implements Transactions {
    int calls;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        calls++;
        return work.get();
    }
}
