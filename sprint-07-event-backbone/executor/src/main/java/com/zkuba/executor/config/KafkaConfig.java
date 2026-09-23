package com.zkuba.executor.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zkuba.executor.client.PricingUnavailableException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;

@Configuration
public class KafkaConfig {

    /**
     * Configures the DefaultErrorHandler that Spring Kafka auto-wires into
     * the ConcurrentKafkaListenerContainerFactory.
     *
     * Fatal exceptions → dead-lettered to orders.DLT immediately (no retry).
     * PricingUnavailableException → indefinite fixed-backoff (Fauxnance outage
     *   must not dead-letter an order — it would stay NEW forever).
     * All other exceptions → exponential backoff up to 30 s, then DLT.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<?, ?> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> r, Exception e) ->
                        new TopicPartition(r.topic() + ".DLT", r.partition()));

        // Append the original exception class name as a header so consumers
        // can inspect the failure reason without re-parsing the value.
        recoverer.setAppendOriginalHeaders(true);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer);

        // --- Fatal classes: dead-letter on the very first attempt ---
        // • JsonProcessingException  – malformed JSON, will never parse
        // • IllegalArgumentException – missing orderId or unexpected event type
        // • NoSuchElementException   – order ID not present in Postgres
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                NoSuchElementException.class,
                JsonProcessingException.class
        );

        // --- BackOff strategy (per-record, chosen at first delivery) ---
        ExponentialBackOff transientBackOff = new ExponentialBackOff(1_000, 2.0);
        transientBackOff.setMaxInterval(10_000);
        transientBackOff.setMaxElapsedTime(30_000); // budget exhausted → DLT

        BiFunction<ConsumerRecord<?, ?>, Exception, BackOff> backOffFunction = (record, ex) -> {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof PricingUnavailableException) {
                    // Fauxnance outage: retry indefinitely every 5 s —
                    // dead-lettering would leave the order stuck at NEW.
                    return new FixedBackOff(5_000, FixedBackOff.UNLIMITED_ATTEMPTS);
                }
                cause = cause.getCause();
            }
            return transientBackOff;
        };

        handler.setBackOffFunction(backOffFunction);

        return handler;
    }
}