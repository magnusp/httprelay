package se.fortnox.httprelay.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
	private static final Mac    mac;
	private static final Logger log = LoggerFactory.getLogger(SignatureValidator.class);

	static {
		try {
			mac = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean       validateSignature;
	private SecretKeySpec secretKey;

	@Autowired
	public SignatureValidator(@Value("${shared_secret}") String sharedSecret, @Value("${validate_signature:true}") boolean validateSignature) {
		this.validateSignature = validateSignature;
		if (sharedSecret == null && this.validateSignature) {
			throw new IllegalStateException("Missing slack.shared_secret but slack.validate_signature states that signatures must be validated");
		} else if (this.validateSignature) {
			secretKey = new SecretKeySpec(
				sharedSecret.getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"
			);
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

	private static Mac createMac() {
		try {
			return (Mac)mac.clone();
		} catch (CloneNotSupportedException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean validate(byte[] signature, Consumer<Mac> digester) throws InvalidKeyException {
		if (!validateSignature) {
            log.warn("Skipping signature validation.");
			return true;
		}
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
