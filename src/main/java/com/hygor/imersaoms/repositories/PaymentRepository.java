package com.hygor.imersaoms.repositories;

import com.hygor.imersaoms.models.Payment;
import com.hygor.imersaoms.models.Payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRepository {

    private final Database database;

    public Mono<Payment> createPayment(final String userId) {
        final Payment payment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .status(PaymentStatus.PENDING)
                .build();

        return Mono.fromCallable(() -> {
            log.info("Saving payment transaction for user {}", userId);
            return this.database.save(userId, payment);
        }).subscribeOn(Schedulers.boundedElastic())
                .doOnNext(next -> log.info("Payment received {}", next.getUserId()));
    }

    public Mono<Payment> getPayment(final String userId) {
        return Mono.defer(() -> {
            log.info("Getting payment from database - {}", userId);
            final Optional<Payment> payment = this.database.get(userId, Payment.class);
            return Mono.justOrEmpty(payment);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(it -> log.info("Payment received - {}", userId));
    }

    public Mono<Payment> processPayment(final String key, final PaymentStatus status) {
        log.info("On payment {} received to status {}", key, status);
        return getPayment(key)
                .flatMap(payment -> Mono.fromCallable(() -> {
                                    log.info("Processing payment {} to status {}", key, status);
                                    return this.database.save(key, payment.withStatus(status));
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                );
    }
}
