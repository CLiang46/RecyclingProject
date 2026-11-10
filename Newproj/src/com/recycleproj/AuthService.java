package com.recycleproj;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User registration, login and session tokens.
 * Only a SHA-256 hash of each session token is stored, so a leaked database cannot be used to log in.
 */
public class AuthService {
	public static final Duration SESSION_LENGTH = Duration.ofDays(7);
	static final int MIN_PASSWORD_LENGTH = 8;
	static final int MAX_PASSWORD_LENGTH = 200;
	static final int MAX_FAILURES = 5;
	static final Duration LOCKOUT = Duration.ofMinutes(5);
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,32}");
	private static final SecureRandom RANDOM = new SecureRandom();

	public static final class User {
		public final long id;
		public final String username;

		User(long id, String username) {
			this.id = id;
			this.username = username;
		}
	}

	/** Thrown when a login or registration is refused; the message is safe to show to the user. */
	public static final class AuthException extends Exception {
		private static final long serialVersionUID = 1L;
		final int status;

		AuthException(int status, String message) {
			super(message);
			this.status = status;
		}
	}

	private static final class Failures {
		int count;
		Instant lockedUntil = Instant.MIN;
	}

	private final Database db;
	private final PasswordHasher hasher;
	private final Map<String, Failures> failures = new HashMap<>();
	/** Hash compared against when the username doesn't exist, so timing doesn't reveal valid usernames. */
	private final String dummyHash;

	public AuthService(Database db) {
		this(db, new PasswordHasher());
	}

	AuthService(Database db, PasswordHasher hasher) {
		this.db = db;
		this.hasher = hasher;
		this.dummyHash = hasher.hash("not-a-real-password");
	}

	public User register(String username, String password) throws AuthException, SQLException {
		if (username == null || !USERNAME.matcher(username).matches()) {
			throw new AuthException(400, "Username must be 3-32 characters: letters, digits, '.', '_' or '-'");
		}
		if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
			throw new AuthException(400, "Password must be " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH
					+ " characters");
		}
		String hash = hasher.hash(password);
		synchronized (db) {
			try (PreparedStatement find = db.connection().prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
				find.setString(1, username);
				try (ResultSet rs = find.executeQuery()) {
					if (rs.next()) {
						throw new AuthException(409, "That username is taken");
					}
				}
			}
			try (PreparedStatement insert = db.connection().prepareStatement(
					"INSERT INTO users (username, password_hash, created_at) VALUES (?, ?, ?)",
					PreparedStatement.RETURN_GENERATED_KEYS)) {
				insert.setString(1, username);
				insert.setString(2, hash);
				insert.setString(3, Instant.now().toString());
				insert.executeUpdate();
				try (ResultSet keys = insert.getGeneratedKeys()) {
					keys.next();
					return new User(keys.getLong(1), username);
				}
			}
		}
	}

	public User login(String username, String password) throws AuthException, SQLException {
		if (username == null || password == null || password.length() > MAX_PASSWORD_LENGTH) {
			throw new AuthException(401, "Wrong username or password");
		}
		String key = username.toLowerCase(java.util.Locale.ROOT);
		synchronized (failures) {
			Failures f = failures.get(key);
			if (f != null && Instant.now().isBefore(f.lockedUntil)) {
				throw new AuthException(429, "Too many failed attempts. Try again in a few minutes.");
			}
		}
		long id = -1;
		String storedName = null;
		String storedHash = dummyHash;
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"SELECT id, username, password_hash FROM users WHERE username = ?")) {
				ps.setString(1, username);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						id = rs.getLong(1);
						storedName = rs.getString(2);
						storedHash = rs.getString(3);
					}
				}
			}
		}
		boolean ok = hasher.verify(password, storedHash) && id >= 0;
		synchronized (failures) {
			if (ok) {
				failures.remove(key);
			} else {
				Failures f = failures.computeIfAbsent(key, k -> new Failures());
				if (++f.count >= MAX_FAILURES) {
					f.count = 0;
					f.lockedUntil = Instant.now().plus(LOCKOUT);
				}
			}
		}
		if (!ok) {
			throw new AuthException(401, "Wrong username or password");
		}
		return new User(id, storedName);
	}

	/** Creates a session and returns the raw token to put in the cookie. */
	public String createSession(User user) throws SQLException {
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		long now = Instant.now().getEpochSecond();
		synchronized (db) {
			try (PreparedStatement purge = db.connection().prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
				purge.setLong(1, now);
				purge.executeUpdate();
			}
			try (PreparedStatement ps = db.connection().prepareStatement(
					"INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)")) {
				ps.setString(1, sha256(token));
				ps.setLong(2, user.id);
				ps.setLong(3, now + SESSION_LENGTH.getSeconds());
				ps.executeUpdate();
			}
		}
		return token;
	}

	/** Returns the logged-in user for a session token, or null if the token is missing, unknown or expired. */
	public User userForToken(String token) throws SQLException {
		if (token == null || token.isEmpty() || token.length() > 100) {
			return null;
		}
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"SELECT u.id, u.username FROM sessions s JOIN users u ON u.id = s.user_id "
							+ "WHERE s.token_hash = ? AND s.expires_at > ?")) {
				ps.setString(1, sha256(token));
				ps.setLong(2, Instant.now().getEpochSecond());
				try (ResultSet rs = ps.executeQuery()) {
					return rs.next() ? new User(rs.getLong(1), rs.getString(2)) : null;
				}
			}
		}
	}

	public void logout(String token) throws SQLException {
		if (token == null) {
			return;
		}
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM sessions WHERE token_hash = ?")) {
				ps.setString(1, sha256(token));
				ps.executeUpdate();
			}
		}
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
