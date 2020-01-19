package se.fortnox.httprelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@RestController
public class SlackController {
    private static final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

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
