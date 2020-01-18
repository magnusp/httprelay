package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.fortnox.httprelay.server.entity.EventCallback;
import se.fortnox.httprelay.server.entity.SlackUrlVerification;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.springframework.core.ResolvableType.forClass;
import static reactor.core.publisher.Mono.defer;
import static reactor.core.publisher.Mono.just;

@RestController
public class SlackController {

	private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();
	private final Logger              log = LoggerFactory.getLogger(SlackController.class);
	public static final ResolvableType RESOLVABLE_TYPE = forClass(SlackUrlVerification.class);
	public static final ResolvableType EVENTCALLBACK_RESOLVABLE_TYPE = forClass(EventCallback.class);

	@PostMapping("/webhook")
	public Mono<Void> acceptWebHook(ServerWebExchange webExchange) {
		return webExchange.getRequest()
			.getBody()
			.next()
			.flatMap(body -> {

				return Flux.merge(
					handleUrlVerification(webExchange, body),
					handleWebhookCall(webExchange, body)
				)
					.then(defer(() -> {
						webExchange.getResponse().setStatusCode(HttpStatus.OK);
						return Mono.empty();
					}));
			});
	}

	private Mono<Void> handleWebhookCall(ServerWebExchange webExchange, DataBuffer body) {
		//String webHookBody = StandardCharsets.UTF_8.decode(body.asByteBuffer()).toString();
		//log.info(webHookBody);
		return decoder
			.decodeToMono(just(body), EVENTCALLBACK_RESOLVABLE_TYPE, MediaType.APPLICATION_JSON, Collections.emptyMap()).cast(EventCallback.class)
			.doOnNext(eventCallback -> {
				log.info(eventCallback.getEvent().getType());
			})
			.then()
			.onErrorResume(throwable -> Mono.empty());
	}

	private Mono<Void> handleUrlVerification(ServerWebExchange webExchange, DataBuffer body) {
		return decoder
			.decodeToMono(just(body), RESOLVABLE_TYPE, MediaType.APPLICATION_JSON, Collections.emptyMap()).cast(SlackUrlVerification.class)
			.flatMap(slackUrlVerification -> {
				byte[]     bytes  = slackUrlVerification.getChallenge().getBytes(StandardCharsets.UTF_8);
				DataBuffer buffer = webExchange.getResponse().bufferFactory().wrap(bytes);
				return webExchange.getResponse().writeWith(Flux.just(buffer));
			})
			.onErrorResume(throwable -> Mono.empty());
	}
}
