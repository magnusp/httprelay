package se.fortnox.httprelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.Objects;

import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.springframework.web.reactive.function.server.ServerResponse.status;
import static reactor.core.publisher.Mono.just;

@Component
public class WebhookHandler implements HandlerFunction<ServerResponse> {
    private final Logger log = LoggerFactory.getLogger(WebhookHandler.class);
    private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();

    private final SignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;
    private final DataPublisher dataPublisher;
    private final DataBufferFactory dataBufferFactory;

    @Autowired
    public WebhookHandler(SignatureValidator signatureValidator, ObjectMapper objectMapper, DataPublisher dataPublisher) {
        this.signatureValidator = signatureValidator;
        this.objectMapper = objectMapper;
        this.dataPublisher = dataPublisher;
        this.dataBufferFactory = new DefaultDataBufferFactory();
    }

    @NotNull
    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        Mono<DataBuffer> bodyProcessor = request.headers().contentType().map(mediaType -> {
            if (mediaType.equals(MediaType.APPLICATION_FORM_URLENCODED)) {
                return request.formData()
                        .map(formData -> dataBufferFactory.wrap(Objects.requireNonNull(formData.getFirst("payload")).getBytes(StandardCharsets.UTF_8)));
            }
            return null;
        }).orElse(request.bodyToMono(DataBuffer.class));

        return bodyProcessor
                .flatMap(dataBuffer -> {
                    HttpHeaders httpHeaders = request.headers().asHttpHeaders();

                    String requestTimestamp = httpHeaders.getFirst("X-Slack-Request-Timestamp");
                    String stringSignature = httpHeaders.getFirst("X-Slack-Signature");
                    if(requestTimestamp == null || stringSignature == null) {
                        return badRequest()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(just("Missing required headers"), String.class);
                    }
                    byte[] signature = stringSignature
                            .replace("v0=", "")
                            .getBytes(StandardCharsets.UTF_8);

                    boolean isValidSignature;
                    try {
                        isValidSignature = signatureValidator.validate(signature, digest -> {
                            digest.update(("v0:" + requestTimestamp + ":").getBytes(StandardCharsets.UTF_8));
                            digest.update(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                        });
                    } catch (InvalidKeyException e) {
                        log.error("Error setting up algorithm", e);
                        return status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                    if (!isValidSignature) {
                        return badRequest()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(just("Invalid signature"), String.class);
                    }
                    return respondToEvent(dataBuffer);
                });
    }

    private Mono<ServerResponse> respondToEvent(DataBuffer dataBuffer) {
        JsonNode requestBody = (JsonNode) decoder.decode(dataBuffer, ResolvableType.forClass(JsonNode.class), MediaType.APPLICATION_JSON, Collections.emptyMap());
        JsonNode type = requestBody.get("type");
        if (!type.isValueNode()) {
            return status(HttpStatus.BAD_REQUEST).build();
        } else if ("url_verification".equals(type.asText())) {

            byte[] bytes = requestBody.get("challenge").asText().getBytes(StandardCharsets.UTF_8);

            return ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(BodyInserters.fromValue(bytes));
        }
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(requestBody)) // Blocking call
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(dataPublisher::provide)
                .flatMap(bytes -> ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(bytes)));
    }
}
