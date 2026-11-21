package fr.walid.acd.review.adapter;

import fr.walid.acd.review.application.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

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
