package se.fortnox.httprelay.server.web.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Flux;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SignedHttpRequest extends ServerHttpRequestDecorator {
    private final Logger log = LoggerFactory.getLogger(SignedHttpRequest.class);
    private final Key secretKey;
    private final String requestTimestamp;
    private final String signature;
    private ServerHttpResponse response;

    public SignedHttpRequest(SecretKeySpec secretKey, ServerHttpRequest delegate, String requestTimestamp, String signature) {
        super(delegate);
        this.secretKey = secretKey;
        this.requestTimestamp = requestTimestamp;
        this.signature = signature;
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

    @Override
    public Flux<DataBuffer> getBody() {
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
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return Flux.empty();
        }

        return super.getBody()
                .log()
                .flatMap(dataBuffer -> {
                    digest.update(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                    if (!verifySignature(digest)) {
                        response.setStatusCode(HttpStatus.BAD_REQUEST);
                        return Flux.empty();
                    }
                    return Flux.just(dataBuffer);
                });
    }

    private boolean verifySignature(Mac digest) {
        String hmac = bytesToHex(digest.doFinal());
        digest.reset();

        return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), signature.replace("v0=", "").getBytes(StandardCharsets.UTF_8));
    }

    public void setResponse(ServerHttpResponse response) {
        this.response = response;
    }
}
