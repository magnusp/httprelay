package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.status;
import static reactor.core.publisher.Mono.just;

@Component
public class SignatureValidator {
    private final Logger log = LoggerFactory.getLogger(SignatureValidator.class);
    private SecretKeySpec secretKey;

    public SignatureValidator() {
    }

    @Autowired
    public SignatureValidator(Environment env) {
        secretKey = new SecretKeySpec(
                env.getProperty("SLACK_SHARED_SECRET").getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
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

    public Mono<ServerResponse> validate(byte[] signature, Consumer<Mac> digester) {
        final Mac digest;
        try {
            digest = Mac.getInstance("HmacSHA256");
            digest.init(secretKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error setting up algorithm", e);
            return status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        digester.accept(digest);

        if (!verifySignature(digest, signature)) {
            return badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(just("Invalid signature"), String.class);
        }
        return Mono.empty();
    }

    private boolean verifySignature(Mac digest, byte[] signature) {
        String hmac = bytesToHex(digest.doFinal());
        digest.reset();

        return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), signature);
    }
}
