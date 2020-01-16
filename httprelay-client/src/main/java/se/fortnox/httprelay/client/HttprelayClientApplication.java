package se.fortnox.httprelay.client;

import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.ClientRSocketFactoryConfigurer;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class HttprelayClientApplication {
    private final Logger log = LoggerFactory.getLogger(HttprelayClientApplication.class);
    private RSocket socket;

    public static void main(String[] args) {
        SpringApplication.run(HttprelayClientApplication.class, args);
    }

    @Bean
    public Mono<RSocketRequester> rSocketRequester(RSocketRequester.Builder builder, RSocketStrategies rSocketStrategies, ClientController clientController) {
        ClientRSocketFactoryConfigurer clientRSocketFactoryConfigurer = RSocketMessageHandler.clientResponder(rSocketStrategies, clientController);
        return builder
                .dataMimeType(MediaType.APPLICATION_CBOR)
                .rsocketFactory(clientRSocketFactoryConfigurer)
                .connectTcp("localhost", 7000)
                .retry(5)
                .cache();
    }

    @Bean
    public ApplicationRunner consumer(Mono<RSocketRequester> requester) {
        return args -> {
            requester
                    .flatMapMany(this::establish)
                    .subscribe(log::info);
        };
    }

    private Flux<String> establish(RSocketRequester requester) {
        return requester
                .route("/lobby")
                .data(DefaultPayload.create(""))
                .retrieveFlux(String.class);
    }
}
