package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.port.out.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Gives the application an explicit transaction boundary without importing Spring into it. Work
 * already running inside a transaction joins it instead of starting a second one.
 */
@Component
final class SpringTransactions implements Transactions {
    private final TransactionTemplate template;

    SpringTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
