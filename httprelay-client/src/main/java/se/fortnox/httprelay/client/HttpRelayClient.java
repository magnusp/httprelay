package se.fortnox.httprelay.client;

import io.rsocket.metadata.WellKnownMimeType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;

@Component
public class HttpRelayClient {
    private static final MimeType SIMPLE_AUTH = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());
    private static final RetryBackoffSpec RETRY_BACKOFF_SPEC = Retry.fixedDelay(Integer.MAX_VALUE, Duration.ofSeconds(1L));
    private final RSocketRequester requester;
    private final KafkaSender<Integer, byte[]> kafkaSender;

    @Autowired
    public HttpRelayClient(RSocketRequester.Builder builder, KafkaSender<Integer, byte[]> kafkaSender, @Value("${httprelay.server.port}") Integer port) {
        this.kafkaSender = kafkaSender;
        UsernamePasswordMetadata user = new UsernamePasswordMetadata("user", "user");
        this.requester = builder
                .setupMetadata(user, SIMPLE_AUTH)
                .rsocketConnector(rSocketConnector -> rSocketConnector.reconnect(RETRY_BACKOFF_SPEC))
                .dataMimeType(MediaType.APPLICATION_CBOR)
                .tcp("127.0.0.1", port);
    }

    public Flux<Void> establish() {
        return requester.route("/events")
                .retrieveFlux(byte[].class)
                .flatMap(s -> {
                    SenderRecord<Integer, byte[], Integer> message = SenderRecord.create(new ProducerRecord<>("slacksink", s), 1);
                    return kafkaSender
                            .send(Mono.just(message))
                            .then();
                });
    }
}
