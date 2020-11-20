package se.fortnox.httprelay.client;

import io.rsocket.metadata.WellKnownMimeType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.cbor.Jackson2CborDecoder;
import org.springframework.http.codec.cbor.Jackson2CborEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;

@SpringBootApplication
public class HttpRelayClient {
	private static final MimeType SIMPLE_AUTH = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());

	public static void main(String[] args) {
		SpringApplication.run(HttpRelayClient.class, args);
	}

	RetryBackoffSpec RETRY_BACKOFF_SPEC = Retry.fixedDelay(Integer.MAX_VALUE, Duration.ofSeconds(1L));

	@Bean
	public RSocketStrategies rSocketStrategies() {
		return RSocketStrategies.builder()
			.encoders(encoders -> {
				encoders.add(new Jackson2CborEncoder());
				encoders.add(new SimpleAuthenticationEncoder());
			})
			.decoders(decoders -> decoders.add(new Jackson2CborDecoder()))
			.build();
	}

	@Bean
	public RSocketRequester rSocketRequester(RSocketRequester.Builder builder) {
		UsernamePasswordMetadata user = new UsernamePasswordMetadata("user", "user");

		return builder
			.setupMetadata(user, SIMPLE_AUTH)
			.rsocketConnector(rSocketConnector -> rSocketConnector.reconnect(RETRY_BACKOFF_SPEC))
			.dataMimeType(MediaType.APPLICATION_CBOR)
			.tcp("127.0.0.1", 7000);
	}

	@Bean
	@ConditionalOnProperty(
		prefix = "application.runner",
		value = "enabled",
		havingValue = "true",
		matchIfMissing = true)
	public ApplicationRunner consumer(Mono<RSocketRequester> requester, ClientController clientController) {
		return args -> requester
			.flatMapMany(clientController::establish)
			.retry()
			.subscribe();
	}

}
