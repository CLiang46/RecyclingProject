package com.recycleproj;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing. Stored format: pbkdf2$iterations$salt$hash (Base64).
 */
public final class PasswordHasher {
	static final int DEFAULT_ITERATIONS = 600_000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BITS = 256;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final int iterations;

	public PasswordHasher() {
		this(DEFAULT_ITERATIONS);
	}

	/** Lower iteration counts are only meant for tests. */
	PasswordHasher(int iterations) {
		this.iterations = iterations;
	}

	public String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] hash = derive(password, salt, iterations);
		Base64.Encoder b64 = Base64.getEncoder();
		return "pbkdf2$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
	}

	public boolean verify(String password, String stored) {
		String[] parts = stored.split("\\$");
		if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
			return false;
		}
		try {
			int storedIterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] expected = Base64.getDecoder().decode(parts[3]);
			return MessageDigest.isEqual(expected, derive(password, salt, storedIterations));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static byte[] derive(String password, byte[] salt, int iterations) {
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
		} finally {
			spec.clearPassword();
		}
	}
}
