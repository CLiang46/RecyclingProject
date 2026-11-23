package com.recycleproj;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User registration, login with an emailed verification code (two-factor authentication) and session tokens.
 *
 * Signing up or logging in is two steps: the password step returns a {@link Challenge} and emails a 6-digit code,
 * and {@link #verify} with that code finishes it. An account is only created once its email address is verified.
 * Only SHA-256 hashes of session tokens and codes are kept, so a leaked database or memory dump can't be used to
 * log in.
 */
public class AuthService {
	public static final Duration SESSION_LENGTH = Duration.ofDays(7);
	static final int MIN_PASSWORD_LENGTH = 8;
	static final int MAX_PASSWORD_LENGTH = 200;
	static final int MAX_FAILURES = 5;
	static final Duration LOCKOUT = Duration.ofMinutes(5);
	static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
	static final Duration RESEND_DELAY = Duration.ofSeconds(30);
	static final int MAX_CODE_ATTEMPTS = 5;
	static final int MAX_SENDS = 5;
	private static final int MAX_PENDING = 10_000;
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,32}");
	private static final Pattern EMAIL = Pattern.compile(
			"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");
	private static final SecureRandom RANDOM = new SecureRandom();

	public static final class User {
		public final long id;
		public final String username;

		User(long id, String username) {
			this.id = id;
			this.username = username;
		}
	}

	/** Returned after the password step. */
	public static final class Challenge {
		/** Opaque id the browser sends back with the code. */
		public final String id;
		/** Partly hidden address the code was sent to, or null when the account still needs an email address. */
		public final String sentTo;

		Challenge(String id, String sentTo) {
			this.id = id;
			this.sentTo = sentTo;
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

	/** A sign-up or login waiting for its code. Guarded by the {@code pending} map's lock. */
	private static final class Pending {
		final boolean registration;
		final String username;
		final long userId;
		final String passwordHash;
		/** True for older accounts that have no email address yet; the verified address is saved on success. */
		final boolean needsEmail;
		String email;
		String codeHash;
		Instant expires;
		Instant lastSentAt;
		int attempts;
		int sends;

		Pending(boolean registration, String username, long userId, String passwordHash, String email) {
			this.registration = registration;
			this.username = username;
			this.userId = userId;
			this.passwordHash = passwordHash;
			this.email = email;
			this.needsEmail = !registration && email == null;
		}
	}

	private final Database db;
	private final PasswordHasher hasher;
	private final Mailer mailer;
	private final Clock clock;
	private final Map<String, Failures> failures = new HashMap<>();
	private final Map<String, Pending> pending = new HashMap<>();
	/** When a code was last emailed to each address, to stop sign-ups being used to flood someone's inbox. */
	private final Map<String, Instant> lastSent = new HashMap<>();
	/** Hash compared against when the username doesn't exist, so timing doesn't reveal valid usernames. */
	private final String dummyHash;

	public AuthService(Database db, Mailer mailer) {
		this(db, new PasswordHasher(), mailer, Clock.systemUTC());
	}

	AuthService(Database db, PasswordHasher hasher, Mailer mailer, Clock clock) {
		this.db = db;
		this.hasher = hasher;
		this.mailer = mailer;
		this.clock = clock;
		this.dummyHash = hasher.hash("not-a-real-password");
	}

	static boolean isValidEmail(String email) {
		return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
	}

	/** Step 1 of sign-up: checks the details and emails a code. The account is created by {@link #verify}. */
	public Challenge startRegistration(String username, String email, String password) throws AuthException, SQLException {
		if (username == null || !USERNAME.matcher(username).matches()) {
			throw new AuthException(400, "Username must be 3-32 characters: letters, digits, '.', '_' or '-'");
		}
		String address = checkEmail(email);
		if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
			throw new AuthException(400, "Password must be " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH
					+ " characters");
		}
		if (usernameTaken(username)) {
			throw new AuthException(409, "That username is taken");
		}
		return sendCode(newChallengeId(), new Pending(true, username, -1, hasher.hash(password), address));
	}

	/**
	 * Step 1 of login: checks the password, then emails a code to the account's address. For an older account with
	 * no address, the returned challenge has a null {@code sentTo}; call {@link #addEmail} next.
	 */
	public Challenge startLogin(String username, String password) throws AuthException, SQLException {
		Account account = checkPassword(username, password);
		Pending p = new Pending(false, account.username, account.id, null, account.email);
		String id = newChallengeId();
		if (p.needsEmail) {
			synchronized (pending) {
				store(id, p);
			}
			return new Challenge(id, null);
		}
		return sendCode(id, p);
	}

	/** For an older account without an email address: sets the address to verify and emails a code to it. */
	public Challenge addEmail(String challengeId, String email) throws AuthException {
		String address = checkEmail(email);
		Pending p;
		synchronized (pending) {
			p = find(challengeId);
			if (!p.needsEmail) {
				throw new AuthException(400, "This account already has an email address");
			}
			p.email = address;
		}
		return sendCode(challengeId, p);
	}

	/** Emails a new code; the previous one stops working. */
	public Challenge resend(String challengeId) throws AuthException {
		Pending p;
		synchronized (pending) {
			p = find(challengeId);
			if (p.email == null) {
				throw new AuthException(400, "Enter your email address first");
			}
		}
		return sendCode(challengeId, p);
	}

	/** Step 2: checks the code and returns the user to start a session for. Creates the account for a sign-up. */
	public User verify(String challengeId, String code) throws AuthException, SQLException {
		Pending p;
		synchronized (pending) {
			p = find(challengeId);
			if (p.codeHash == null) {
				throw new AuthException(400, "Enter your email address first");
			}
			String given = code == null ? "" : code.replaceAll("\\s", "");
			boolean ok = MessageDigest.isEqual(sha256(given).getBytes(StandardCharsets.US_ASCII),
					p.codeHash.getBytes(StandardCharsets.US_ASCII));
			if (!ok) {
				p.attempts++;
				if (!p.registration) {
					recordFailure(p.username);
				}
				if (p.attempts >= MAX_CODE_ATTEMPTS) {
					pending.remove(challengeId);
					throw new AuthException(401, "Too many wrong codes. Please start again.");
				}
				throw new AuthException(401, "That code isn't right. Check the email and try again.");
			}
			pending.remove(challengeId);
		}
		if (p.registration) {
			return createUser(p.username, p.email, p.passwordHash);
		}
		if (p.needsEmail) {
			saveEmail(p.userId, p.email);
		}
		clearFailures(p.username);
		return new User(p.userId, p.username);
	}

	/** Creates a session and returns the raw token to put in the cookie. */
	public String createSession(User user) throws SQLException {
		String token = randomToken();
		long now = clock.instant().getEpochSecond();
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
				ps.setLong(2, clock.instant().getEpochSecond());
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

	/** "jane.doe@example.com" becomes "j******e@example.com". */
	static String maskEmail(String email) {
		int at = email.indexOf('@');
		String local = email.substring(0, at);
		String hidden = local.length() <= 2 ? local.charAt(0) + "*"
				: local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
		return hidden + email.substring(at);
	}

	private static final class Account {
		final long id;
		final String username;
		final String email;

		Account(long id, String username, String email) {
			this.id = id;
			this.username = username;
			this.email = email;
		}
	}

	private Account checkPassword(String username, String password) throws AuthException, SQLException {
		if (username == null || password == null || password.length() > MAX_PASSWORD_LENGTH) {
			throw new AuthException(401, "Wrong username or password");
		}
		checkNotLocked(username);
		long id = -1;
		String storedName = null;
		String storedHash = dummyHash;
		String email = null;
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"SELECT id, username, password_hash, email FROM users WHERE username = ?")) {
				ps.setString(1, username);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						id = rs.getLong(1);
						storedName = rs.getString(2);
						storedHash = rs.getString(3);
						email = rs.getString(4);
					}
				}
			}
		}
		if (!hasher.verify(password, storedHash) || id < 0) {
			recordFailure(username);
			throw new AuthException(401, "Wrong username or password");
		}
		return new Account(id, storedName, email);
	}

	private Challenge sendCode(String id, Pending p) throws AuthException {
		String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
		String to = p.email;
		Instant now = clock.instant();
		synchronized (pending) {
			if (p.sends >= MAX_SENDS) {
				pending.remove(id);
				throw new AuthException(429, "Too many codes sent. Please start again.");
			}
			Instant wait = p.lastSentAt == null ? null : p.lastSentAt.plus(RESEND_DELAY);
			// Unverified addresses also get a per-address limit, so sign-ups can't be used to flood someone's inbox.
			Instant last = p.registration || p.needsEmail ? lastSent.get(to.toLowerCase(Locale.ROOT)) : null;
			if (last != null && (wait == null || last.plus(RESEND_DELAY).isAfter(wait))) {
				wait = last.plus(RESEND_DELAY);
			}
			if (wait != null && now.isBefore(wait)) {
				long seconds = Duration.between(now, wait).toSeconds() + 1;
				throw new AuthException(429, "Please wait " + seconds + " seconds before asking for another code.");
			}
			store(id, p);
			lastSent.put(to.toLowerCase(Locale.ROOT), now);
			p.lastSentAt = now;
			p.sends++;
			p.attempts = 0;
			p.codeHash = sha256(code);
			p.expires = now.plus(CODE_LIFETIME);
		}
		String purpose = p.registration ? "create an account" : "log in";
		String body = "Your Recycling Buyback Calculator verification code is:\n\n    " + code + "\n\n"
				+ "Enter it on the page where you tried to " + purpose + ". It expires in " + CODE_LIFETIME.toMinutes()
				+ " minutes.\n\n"
				+ (p.registration ? "If you didn't try to create an account, you can ignore this email.\n"
						: "If you didn't try to log in, someone else may know your password.\n");
		try {
			mailer.send(to, "Your verification code: " + code, body);
		} catch (IOException | RuntimeException e) {
			System.err.println("Couldn't send verification email to " + to + ": " + e.getMessage());
			synchronized (pending) {
				pending.remove(id);
				lastSent.remove(to.toLowerCase(Locale.ROOT));
			}
			throw new AuthException(502, "Couldn't send the verification email. Check the address or try again later.");
		}
		return new Challenge(id, maskEmail(to));
	}

	/** Adds or keeps a challenge, dropping expired ones. Caller holds the {@code pending} lock. */
	private void store(String id, Pending p) throws AuthException {
		Instant now = clock.instant();
		pending.values().removeIf(other -> other.expires.isBefore(now));
		lastSent.values().removeIf(t -> t.plus(RESEND_DELAY).isBefore(now));
		if (!pending.containsKey(id) && pending.size() >= MAX_PENDING) {
			throw new AuthException(503, "Too many sign-ins in progress. Please try again in a few minutes.");
		}
		if (p.expires == null) {
			p.expires = now.plus(CODE_LIFETIME);
		}
		pending.put(id, p);
	}

	/** Caller holds the {@code pending} lock. */
	private Pending find(String challengeId) throws AuthException {
		Pending p = challengeId == null ? null : pending.get(challengeId);
		if (p == null || p.expires.isBefore(clock.instant())) {
			if (p != null) {
				pending.remove(challengeId);
			}
			throw new AuthException(400, "This code has expired. Please start again.");
		}
		return p;
	}

	private static String checkEmail(String email) throws AuthException {
		String address = email == null ? "" : email.trim();
		if (!isValidEmail(address)) {
			throw new AuthException(400, "Enter a valid email address");
		}
		return address;
	}

	private boolean usernameTaken(String username) throws SQLException {
		synchronized (db) {
			try (PreparedStatement find = db.connection().prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
				find.setString(1, username);
				try (ResultSet rs = find.executeQuery()) {
					return rs.next();
				}
			}
		}
	}

	private User createUser(String username, String email, String passwordHash) throws AuthException, SQLException {
		synchronized (db) {
			// Someone else may have taken the name while this sign-up waited for its code.
			if (usernameTaken(username)) {
				throw new AuthException(409, "That username is taken");
			}
			try (PreparedStatement insert = db.connection().prepareStatement(
					"INSERT INTO users (username, password_hash, created_at, email) VALUES (?, ?, ?, ?)",
					PreparedStatement.RETURN_GENERATED_KEYS)) {
				insert.setString(1, username);
				insert.setString(2, passwordHash);
				insert.setString(3, clock.instant().toString());
				insert.setString(4, email);
				insert.executeUpdate();
				try (ResultSet keys = insert.getGeneratedKeys()) {
					keys.next();
					return new User(keys.getLong(1), username);
				}
			}
		}
	}

	private void saveEmail(long userId, String email) throws SQLException {
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement("UPDATE users SET email = ? WHERE id = ?")) {
				ps.setString(1, email);
				ps.setLong(2, userId);
				ps.executeUpdate();
			}
		}
	}

	private void checkNotLocked(String username) throws AuthException {
		synchronized (failures) {
			Failures f = failures.get(username.toLowerCase(Locale.ROOT));
			if (f != null && clock.instant().isBefore(f.lockedUntil)) {
				throw new AuthException(429, "Too many failed attempts. Try again in a few minutes.");
			}
		}
	}

	private void recordFailure(String username) {
		synchronized (failures) {
			Failures f = failures.computeIfAbsent(username.toLowerCase(Locale.ROOT), k -> new Failures());
			if (++f.count >= MAX_FAILURES) {
				f.count = 0;
				f.lockedUntil = clock.instant().plus(LOCKOUT);
			}
		}
	}

	private void clearFailures(String username) {
		synchronized (failures) {
			failures.remove(username.toLowerCase(Locale.ROOT));
		}
	}

	private static String newChallengeId() {
		return randomToken();
	}

	private static String randomToken() {
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
