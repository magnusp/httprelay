package se.fortnox.httprelay.client;

import io.rsocket.RSocket;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import javax.annotation.PreDestroy;

@Controller
public class ClientController {
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

    protected Flux<Void> establish(RSocketRequester requester) {
        rsocket = requester.rsocket();

        return requester
                .route("/events")
                .retrieveFlux(byte[].class)
                .flatMap(s -> {
                    SenderRecord<Integer, byte[], Integer> message = SenderRecord.create(new ProducerRecord<>("hello", s), 1);
                    return kafkaSender
                            .send(Mono.just(message))
                            .then();
                });
    }
}
