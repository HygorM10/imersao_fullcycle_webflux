package com.hygor.imersaoms.publishers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hygor.imersaoms.models.Payment;
import com.hygor.imersaoms.models.PubSubMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class PaymentPublisher {

    private final Sinks.Many<PubSubMessage> sink;
    private final ObjectMapper mapper;

    public Mono<Payment> onPaymentCreate(final Payment payment) {
        return Mono.fromCallable(() -> {
            final String userId = payment.getUserId();
            final String data = mapper.writeValueAsString(payment);
            return new PubSubMessage(userId, data);
        }).subscribeOn(Schedulers.parallel())
                .doOnNext(this.sink::tryEmitNext)
                .thenReturn(payment);
    }

}
