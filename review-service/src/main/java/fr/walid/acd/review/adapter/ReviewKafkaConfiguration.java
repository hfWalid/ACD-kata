package fr.walid.acd.review.adapter;

import fr.walid.acd.review.application.ReviewService;
import fr.walid.acd.review.application.Transactions;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class ReviewKafkaConfiguration {

    @Bean
    ReviewService reviewService(JdbcReviewTasks tasks, Transactions transactions) {
        return new ReviewService(tasks, transactions);
    }

    /**
     * Two retries for a transient failure; a contract error is not retryable because the payload
     * will never become readable. Both end on {@code <topic>.DLT}.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafka) {
        // Explicit destination: <topic>.DLT on the same partition, so the dead letter keeps the
        // ordering of the document it belongs to.
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
        handler.addNotRetryableExceptions(EventContractException.class);
        return handler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
