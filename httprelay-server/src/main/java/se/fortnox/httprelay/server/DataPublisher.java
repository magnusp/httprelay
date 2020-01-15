package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Controller
public class DataPublisher {
    private final Logger log = LoggerFactory.getLogger(DataPublisher.class);
    private final Flux<DataFrame> datastream;

    public DataPublisher() {
		/*this.datastream = Flux.zip(
				Flux.interval(Duration.ofSeconds(1)),
				Flux.just("hej").repeat(),
				(aLong, s) -> {
					return new DataFrame(s + " " + aLong);
				}
		)
				.doOnNext(dataFrame -> log.info(dataFrame.getValue()))
		.publish();
		this.datastream.connect();*/
        this.datastream = Flux
                .interval(Duration.ofSeconds(1))
                .map(aLong -> new DataFrame("sequence " + aLong))
				.onBackpressureLatest()
				.doOnSubscribe(subscription -> {
					log.info("Publishing started");
				})
                .publish()
		.refCount();
    }

    @MessageMapping("data")
    public Flux<DataFrame> getData() {
        return datastream
				.doOnSubscribe(subscription -> log.info("Subscriber connected"))
				.doOnComplete(() -> log.info("Subscriber disconnected"));
    }
}
