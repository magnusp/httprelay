package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.fortnox.httprelay.server.entity.SlackUrlVerification;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.springframework.core.ResolvableType.forClass;

@RestController
public class SlackController {
    private static final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();

    @PostMapping("/webhook")
    public Mono<Void> acceptWebHook(ServerHttpRequest request, ServerHttpResponse response) {
        return decoder.decodeToMono(request.getBody(), forClass(SlackUrlVerification.class), MediaType.APPLICATION_JSON, Collections.emptyMap())
                .cast(SlackUrlVerification.class)
                .flatMap(slackUrlVerification -> {
                    response.setStatusCode(HttpStatus.OK);
                    byte[] bytes = slackUrlVerification.getChallenge().getBytes(StandardCharsets.UTF_8);
                    DataBuffer buffer = dataBufferFactory.wrap(bytes);
                    return response.writeWith(Flux.just(buffer));
                })
                .then();
    }
}
