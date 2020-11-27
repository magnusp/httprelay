package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class DataForwarder {
    private final Logger log = LoggerFactory.getLogger(DataForwarder.class);
    private final DataPublisher dataPublisher;

    @Autowired
    public DataForwarder(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }

    @MessageMapping("/events")
    public Flux<byte[]> events(RSocketRequester requester) {
        return dataPublisher
                .stream()
                .doOnTerminate(() -> {
                    log.info("Server error while streaming data to the client");
                })
                .doOnCancel(() -> {
                    log.info("Connection closed by the client");
                });
    }
}
