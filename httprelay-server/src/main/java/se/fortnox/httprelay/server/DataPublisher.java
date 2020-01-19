package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Date.from;

@RestController
public class DataPublisher {
    private final Logger                      log     = LoggerFactory.getLogger(DataPublisher.class);
    private final EmitterProcessor<DataFrame> datastream;
	private       Queue<RSocketRequester>     clients = new ConcurrentLinkedQueue<>();

	public DataPublisher() {
	    this.datastream = EmitterProcessor
		    .<DataFrame>create();
	    this.datastream
                .flatMap(dataFrame -> Flux.fromIterable(this.clients)
                        .flatMap(requester -> sendDataFrame(requester, dataFrame)))
                .subscribe();
    }

    private Mono<Void> sendDataFrame(RSocketRequester requester, DataFrame dataFrame) {
	    return requester
                .route("/datastream")
                .data(dataFrame)
                .send();
    }

    @GetMapping("/trigger")
    public Mono<Void> trigger() {
    	datastream.onNext(new DataFrame("Value: " + from(Instant.now())));
    	return Mono.empty();
    }

    @MessageMapping("/lobby")
    public Flux<String> getData(RSocketRequester requester) {
    	clients.offer(requester);
        return Flux
                .interval(Duration.ofSeconds(5))
                .flatMap(aLong -> Mono.just("Clients: " + clients.size()))
                .doOnTerminate(() -> {
                    log.info("Server error while streaming data to the client");
                    clients.remove(requester);
                })
                .doOnCancel(() -> {
                    log.info("Connection closed by the client");
                    clients.remove(requester);
                });
    }
}
