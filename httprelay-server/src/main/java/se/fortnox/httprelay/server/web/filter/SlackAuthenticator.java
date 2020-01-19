package se.fortnox.httprelay.server.web.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Component
@Order(HIGHEST_PRECEDENCE)
public class SlackAuthenticator implements WebFilter {
    private final SecretKeySpec secretKey;


    @Autowired
    public SlackAuthenticator(Environment env) {
        secretKey = new SecretKeySpec(
                env.getProperty("SLACK_SHARED_SECRET").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Override
    public Mono<Void> filter(@NotNull ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        ServerHttpRequest request1 = serverWebExchange.getRequest();
        PathPattern match = new PathPatternParser().parse("/webhook");
        if (!match.matches(request1.getPath().pathWithinApplication())) {
            return webFilterChain.filter(serverWebExchange);
        }
        HttpHeaders headers = request1.getHeaders();
        String requestTimestamp = headers.getFirst("X-Slack-Request-Timestamp");
        String signature = headers.getFirst("X-Slack-Signature");
        if (requestTimestamp == null || signature == null) {
            serverWebExchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return Mono.empty();
        }

        SignedHttpRequest signedHttpRequest = new SignedHttpRequest(
                secretKey,
                serverWebExchange.getRequest(),
                requestTimestamp,
                signature
        );
        signedHttpRequest.setResponse(serverWebExchange.getResponse());

        ServerWebExchange decoratedServerWebExchange = serverWebExchange
                .mutate()
                .request(signedHttpRequest)
                .response(serverWebExchange.getResponse())
                .build();

        return webFilterChain
                .filter(decoratedServerWebExchange);
    }
}
