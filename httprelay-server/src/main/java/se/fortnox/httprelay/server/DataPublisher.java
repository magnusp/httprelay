package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.MetadataExtractor;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Date;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@RestController
public class DataPublisher {
    private final Logger                      log     = LoggerFactory.getLogger(DataPublisher.class);
    private final EmitterProcessor<DataFrame> datastream;
	private       Queue<RSocketRequester>     clients = new ConcurrentLinkedQueue<>();

	public DataPublisher() {
	    this.datastream = EmitterProcessor
		    .<DataFrame>create();

        /*this.datastream = Flux
                .interval(Duration.ofSeconds(1))
                .map(aLong -> new DataFrame("sequence " + aLong))
				.onBackpressureLatest()
				.doOnSubscribe(subscription -> {
					log.info("Publishing started");
				})
                .publish()
		.refCount();*/
    }

    @GetMapping("/trigger")
    public Mono<Void> trigger() {
    	datastream.onNext(new DataFrame("Value: " + Date.from(Instant.now())));
    	return Mono.empty();
    }

    @GetMapping("/inform")
    public Mono<Void> inform() {
		return Flux
			.fromIterable(clients)
			.flatMap(requester -> {
				Map<String, String> metadata = Collections.singletonMap("route", "data.inform");

				return requester
					.metadata(metadata, MimeTypeUtils.parseMimeType("message/x.rsocket.routing.v0"))
					.data(new DataFrame("some information")).send();
			})
			.then(Mono.empty());
    }

    @MessageMapping("data")
    public Flux<DataFrame> getData(RSocketRequester requester) {
    	clients.offer(requester);
        return datastream
				.doOnSubscribe(subscription -> log.info("Subscriber connected"))
				.doOnComplete(() -> log.info("Subscriber disconnected"));
    }
}
