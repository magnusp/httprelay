package se.fortnox.httprelay.client;

import io.rsocket.RSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Controller
public class ClientController {
    private final Logger log = LoggerFactory.getLogger(ClientController.class);
    private RSocket rsocket;

    public ClientController() {
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
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .doOnNext(log::info);
    }
}
