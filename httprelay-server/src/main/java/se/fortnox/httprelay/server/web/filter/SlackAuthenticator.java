package se.fortnox.httprelay.server.web.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class SlackAuthenticator implements WebFilter {
    private final Logger log = LoggerFactory.getLogger(SlackAuthenticator.class);
    private final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder();
    private final Key secretKey;

    @Autowired
    public SlackAuthenticator(Environment env) {
        secretKey = new SecretKeySpec(
                env.getProperty("SLACK_SHARED_SECRET").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        PathPattern match = new PathPatternParser().parse("/webhook");

        ServerHttpRequest request = serverWebExchange.getRequest();

        if (!match.matches(request.getPath().pathWithinApplication())) {
            return webFilterChain.filter(serverWebExchange);
        }
        HttpHeaders headers = request.getHeaders();
        String requestTimestamp = headers.getFirst("X-Slack-Request-Timestamp");
        String signature = headers.getFirst("X-Slack-Signature");
        if (requestTimestamp == null || signature == null) {
            return Mono.error(new RuntimeException("Bad parameters"));
        }
        return request.getBody()
                .map(bodyDataBuffer -> {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        Channels.newChannel(baos).write(bodyDataBuffer.asByteBuffer().asReadOnlyBuffer());
                        return verifySignature(signature, "v0", requestTimestamp, baos.toByteArray());
                    } catch (IOException e) {
                        return e;
                    }
                })
                .then(webFilterChain.filter(serverWebExchange));
    }

    private boolean verifySignature(String signature, String version, String timestamp, byte[] body) {
        final Mac digest;
        try {
            digest = Mac.getInstance("HmacSHA256");
            digest.init(secretKey);
            byte[] delimiter = ":".getBytes(StandardCharsets.UTF_8);

            digest.update(version.getBytes(StandardCharsets.UTF_8));
            digest.update(delimiter);
            digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
            digest.update(delimiter);
            digest.update(body);

            String hmac = bytesToHex(digest.doFinal());
            digest.reset();

            return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), signature.replace("v0=", "").getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error setting up algorithm", e);
            return false;
        }
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
}
