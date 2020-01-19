package se.fortnox.httprelay.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.codec.ByteArrayEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import se.fortnox.httprelay.server.entity.SlackUrlVerification;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.springframework.core.ResolvableType.forClass;

@RestController
public class SlackController {
    private static final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();
    private final Jackson2JsonEncoder encoder = new Jackson2JsonEncoder();

    private final ObjectMapper objectMapper;

    @Autowired
    public SlackController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public Mono<Void> acceptWebHook(ServerHttpResponse response, @RequestBody JsonNode requestBody) {
        JsonNode type = requestBody.get("type");
        if (!type.isValueNode()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return Mono.empty();
        } else if ("url_verification".equals(type.asText())) {
            response.setStatusCode(HttpStatus.OK);
            byte[] bytes = requestBody.get("challenge").asText().getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = dataBufferFactory.wrap(bytes);
            return response.writeWith(Flux.just(buffer));
        }
        return Mono.fromCallable(() -> {
            response.setStatusCode(HttpStatus.OK);
            return objectMapper.writeValueAsBytes(requestBody); // Blocking call
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
