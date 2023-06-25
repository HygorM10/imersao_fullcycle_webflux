package com.hygor.imersaoms.controllers;

import com.hygor.imersaoms.models.Payment;
import com.hygor.imersaoms.publishers.PaymentPublisher;
import com.hygor.imersaoms.repositories.InMemoryDatabase;
import com.hygor.imersaoms.repositories.PaymentRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping(value = "payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentPublisher paymentPublisher;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Payment> createPayment(@RequestBody final NewPaymentInput input) {
        final String userId = input.getUserId();
        log.info("Payment to be processed: {}", userId);
        return this.paymentRepository.createPayment(userId)
                .doOnNext(it -> log.info("Next tick - {}", it))
                .flatMap(this.paymentPublisher::onPaymentCreate)
                .flatMap(payment -> Flux.interval(Duration.ofSeconds(1)).flatMap(tick -> this.paymentRepository.getPayment(userId)).filter(it -> Payment.PaymentStatus.APPROVED == it.getStatus()).next())
                .doOnNext(next -> log.info("Payment processed {}", userId))
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).doAfterRetry(signal -> log.info("Execution failed... retrying.. {}", signal.totalRetries())));
    }

    @GetMapping(value = "users")
    public Flux<Payment> findAllById(@RequestParam String ids) {
        final List<String> _ids = Arrays.asList(ids.split(","));
        log.info("Collection {} payments", _ids.size());
        return Flux.fromIterable(_ids)
                .flatMap(this.paymentRepository::getPayment);
    }

    @GetMapping(value = "ids")
    public Mono<String> getIds() {
        return Mono.fromCallable(() -> String.join(",", InMemoryDatabase.DATABASE.keySet()))
                .subscribeOn(Schedulers.parallel());
    }

    @Data
    public static class NewPaymentInput {
        private String userId;
    }
}
