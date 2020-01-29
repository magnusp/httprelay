package se.fortnox.httprelay.client;

import io.rsocket.RSocket;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Controller
public class ClientController {
    private final Logger log = LoggerFactory.getLogger(ClientController.class);
    private final KafkaSender<Integer, byte[]> kafkaSender;
    private RSocket rsocket;

    @Autowired
    public ClientController(KafkaSender<Integer, byte[]> kafkaSender) {
        this.kafkaSender = kafkaSender;
    }

    @PreDestroy
    void destroy() {
        if (rsocket != null) {
            rsocket.dispose();
        }
    }

    protected Flux<String> establish(RSocketRequester requester) {
        rsocket = requester.rsocket();

        return requester
                .route("/events")
                .retrieveFlux(byte[].class)
                .flatMap(s -> {
                    SenderRecord<Integer, byte[], Integer> message = SenderRecord.create(new ProducerRecord<>("hello", s), 1);
                    return kafkaSender
                            .send(Mono.just(message))
                            .doOnNext(senderResult -> log.info(senderResult.toString()))
                            .then(Mono.just(s));
                })
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))

                .doOnNext(log::info);

    }
}
