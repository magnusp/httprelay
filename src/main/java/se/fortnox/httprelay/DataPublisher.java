package se.fortnox.httprelay;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class DataPublisher {

	@MessageMapping("currentMarketData")
	public Mono<String> currentMarketData() {
		return Mono.just("Hej");
	}
}
