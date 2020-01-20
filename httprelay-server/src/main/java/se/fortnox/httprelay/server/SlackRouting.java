package se.fortnox.httprelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.springframework.web.reactive.function.server.ServerResponse.status;
import static reactor.core.publisher.Mono.just;

@Component
public class SlackRouting {
	private final Logger        log = LoggerFactory.getLogger(SlackRouting.class);
	private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();
	private final SecretKeySpec secretKey;
	private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
	private final     ObjectMapper      objectMapper;

	@Autowired
	public SlackRouting(Environment env, ObjectMapper objectMapper) {
		secretKey = new SecretKeySpec(
			env.getProperty("SLACK_SHARED_SECRET").getBytes(StandardCharsets.UTF_8),
			"HmacSHA256"
		);
		this.objectMapper = objectMapper;
	}

	@Bean
	RouterFunction<ServerResponse> getEmployeeByIdRoute() {

		/*return route(POST("/webhook"),
			req -> ok().body(just("Hej"), String.class));*/
		return route(POST("/webhook"), this::handleWebhook);
	}

	private Mono<ServerResponse> handleWebhook(ServerRequest serverRequest) {
		return serverRequest
			.body(BodyExtractors.toDataBuffers(), Collections.emptyMap())
			.concatMap(dataBuffer -> validateSignature(serverRequest.headers(), dataBuffer))
			.concatMap(dataBuffer -> respondToEvent(serverRequest, dataBuffer))
			.next();
		//return ok().body(just("hej"), String.class);
	}

	private Mono<ServerResponse> respondToEvent(ServerRequest serverRequest, DataBuffer dataBuffer) {
		JsonNode requestBody = (JsonNode) decoder.decode(dataBuffer, ResolvableType.forClass(JsonNode.class), MediaType.APPLICATION_JSON, Collections.emptyMap());
		JsonNode type = requestBody.get("type");
		if (!type.isValueNode()) {
			return status(HttpStatus.BAD_REQUEST).build();
		} else if ("url_verification".equals(type.asText())) {

			byte[] bytes = requestBody.get("challenge").asText().getBytes(StandardCharsets.UTF_8);
			//DataBuffer buffer = dataBufferFactory.wrap(bytes);

			return ok()
				.contentType(MediaType.TEXT_PLAIN)
				.body(BodyInserters.fromValue(bytes));
		}
		return Mono.fromCallable(() -> {
			byte[] bytes = objectMapper.writeValueAsBytes(requestBody);// Blocking call
			return ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromValue(bytes));
		})
			.flatMapMany(serverResponseMono -> serverResponseMono)
			.subscribeOn(Schedulers.boundedElastic())
			.next();
	}

	private Flux<DataBuffer> validateSignature(ServerRequest.Headers headers, DataBuffer dataBuffer) {
		String requestTimestamp = headers.asHttpHeaders().getFirst("X-Slack-Request-Timestamp");
		String signature        = headers.asHttpHeaders().getFirst("X-Slack-Signature");

		final Mac digest;
		try {
			digest = Mac.getInstance("HmacSHA256");
			digest.init(secretKey);
			byte[] delimiter = ":".getBytes(StandardCharsets.UTF_8);

			digest.update("v0".getBytes(StandardCharsets.UTF_8));
			digest.update(delimiter);
			digest.update(requestTimestamp.getBytes(StandardCharsets.UTF_8));
			digest.update(delimiter);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			log.error("Error setting up algorithm", e);
			return Flux.error(e);
		}

		digest.update(dataBuffer.asByteBuffer().asReadOnlyBuffer());
		if (!verifySignature(digest, signature)) {
			return Flux.error(new ServerWebInputException("Invalid signature"));
		}
		return Flux.just(dataBuffer);
	}

	private static String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) hexString.append('0');
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private boolean verifySignature(Mac digest, String signature) {
		String hmac = bytesToHex(digest.doFinal());
		digest.reset();

		return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), signature.replace("v0=", "").getBytes(StandardCharsets.UTF_8));
	}
}
