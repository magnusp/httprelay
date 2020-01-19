package se.fortnox.httprelay.client;

import io.rsocket.util.DefaultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.fortnox.httprelay.server.DataFrame;

@Controller
public class ClientController {
    private final Logger log = LoggerFactory.getLogger(ClientController.class);

    @MessageMapping("/datastream")
    public void acceptData(Mono<DataFrame> dataFramePublisher) {
        dataFramePublisher
                .subscribe(dataFrame -> log.info(dataFrame.getValue()));
    }

    protected Flux<String> establish(RSocketRequester requester) {
        return requester
                .route("/lobby")
                .data(DefaultPayload.create(""))
                .retrieveFlux(String.class)
                .doOnNext(log::info);
    }
}
