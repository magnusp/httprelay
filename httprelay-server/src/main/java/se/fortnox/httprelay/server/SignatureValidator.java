package se.fortnox.httprelay.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

@Component
public class SignatureValidator {
    private static final Mac mac;

    static {
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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

    private static Mac createMac() {
        try {
            return (Mac) mac.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean validate(byte[] signature, Consumer<Mac> digester) throws InvalidKeyException {
        final Mac digest = createMac();

        digest.init(secretKey);
        digester.accept(digest);

        return verifySignature(digest, signature);
    }

    private boolean verifySignature(Mac digest, byte[] signature) {
        String hmac = bytesToHex(digest.doFinal());
        digest.reset();

        return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), signature);
    }
}
