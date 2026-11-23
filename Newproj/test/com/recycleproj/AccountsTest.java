package com.recycleproj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tests for accounts, sessions and saved calculations, including end-to-end HTTP requests against a real server.
 * Needs the SQLite driver on the classpath (.\run.ps1 test or ./run.sh test). Exits non-zero on failure.
 */
public class AccountsTest {
	private static int failures = 0;
	private static int passed = 0;
	/** Few iterations keep the tests fast; production uses PasswordHasher.DEFAULT_ITERATIONS. */
	private static final PasswordHasher FAST_HASHER = new PasswordHasher(1000);
	private static final Path WEB_ROOT = Path.of("BuybackCalculatorFrontend", "src", "main", "webapp");

	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory("recycling-test");
		try {
			passwordsAreSaltedAndVerified();
			registrationIsValidated(dir.resolve("register.db"));
			loginCreatesAndEndsSessions(dir.resolve("login.db"));
			repeatedFailuresLockTheAccount(dir.resolve("lockout.db"));
			codesExpireAndCanBeResent(dir.resolve("codes.db"));
			wrongCodesAreLimited(dir.resolve("wrong.db"));
			olderAccountsAddAnEmail(dir.resolve("legacy.db"));
			mailFailureIsReported(dir.resolve("mailfail.db"));
			savedCalculationsArePrivate(dir.resolve("store.db"));
			canonicalInputsKeepOnlyFilledCalculatorFields();
			httpAccountFlow(dir.resolve("http.db"));
			accountsCanBeTurnedOff();
			smtpMailerSpeaksSmtp();
		} finally {
			deleteRecursively(dir);
		}

		System.out.println(passed + " passed, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}

	/** Keeps every email instead of sending it. */
	static final class RecordingMailer implements Mailer {
		final List<String[]> sent = new ArrayList<>();

		@Override
		public synchronized void send(String to, String subject, String body) {
			sent.add(new String[] { to, subject, body });
		}

		synchronized String lastCode(String to) {
			for (int i = sent.size() - 1; i >= 0; i--) {
				if (sent.get(i)[0].equals(to)) {
					Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.get(i)[2]);
					return m.find() ? m.group(1) : null;
				}
			}
			return null;
		}
	}

	/** A clock the tests can move forward. */
	static final class TestClock extends Clock {
		Instant now = Instant.parse("2026-01-01T12:00:00Z");

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	static AuthService newAuth(Database db, Mailer mail, Clock clock) {
		return new AuthService(db, FAST_HASHER, mail, clock);
	}

	/** Signs up through both steps, using name@example.com. */
	static AuthService.User signUp(AuthService auth, RecordingMailer mail, String name) throws Exception {
		AuthService.Challenge c = auth.startRegistration(name, name + "@example.com", "password123");
		return auth.verify(c.id, mail.lastCode(name + "@example.com"));
	}

	static void passwordsAreSaltedAndVerified() {
		String a = FAST_HASHER.hash("correct horse");
		String b = FAST_HASHER.hash("correct horse");
		assertEquals("same password hashes differently (salt)", false, a.equals(b));
		assertEquals("right password verifies", true, FAST_HASHER.verify("correct horse", a));
		assertEquals("wrong password fails", false, FAST_HASHER.verify("correct horsf", a));
		assertEquals("garbage hash fails", false, FAST_HASHER.verify("x", "not-a-hash"));
		assertEquals("plain text is not stored", false, a.contains("correct horse"));
	}

	static void registrationIsValidated(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			AuthService auth = newAuth(db, mail, new TestClock());
			expectAuthError("short username", 400, () -> auth.startRegistration("ab", "a@example.com", "password123"));
			expectAuthError("username with spaces", 400,
					() -> auth.startRegistration("bad name", "a@example.com", "password123"));
			expectAuthError("short password", 400, () -> auth.startRegistration("alice", "a@example.com", "short"));
			expectAuthError("missing email", 400, () -> auth.startRegistration("alice", "", "password123"));
			expectAuthError("bad email", 400, () -> auth.startRegistration("alice", "alice@", "password123"));
			expectAuthError("email with a line break", 400,
					() -> auth.startRegistration("alice", "a@example.com\r\nBcc: x@evil.example", "password123"));
			assertEquals("nothing emailed for invalid sign-ups", 0, mail.sent.size());

			AuthService.Challenge c = auth.startRegistration("alice", " alice@example.com ", "password123");
			assertEquals("code emailed to the trimmed address", "alice@example.com", mail.sent.get(0)[0]);
			assertEquals("masked address", "a***e@example.com", c.sentTo);
			expectAuthError("no account until the code is entered", 401, () -> auth.startLogin("alice", "password123"));
			expectAuthError("wrong code", 401, () -> auth.verify(c.id, "000000".equals(mail.lastCode("alice@example.com"))
					? "111111" : "000000"));
			AuthService.User alice = auth.verify(c.id, mail.lastCode("alice@example.com"));
			assertEquals("registered name", "alice", alice.username);
			expectAuthError("code can't be reused", 400, () -> auth.verify(c.id, mail.lastCode("alice@example.com")));
			expectAuthError("duplicate username, any case", 409,
					() -> auth.startRegistration("ALICE", "other@example.com", "password123"));
		}
	}

	static void loginCreatesAndEndsSessions(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			TestClock clock = new TestClock();
			AuthService auth = newAuth(db, mail, clock);
			signUp(auth, mail, "Bob");
			clock.advance(AuthService.RESEND_DELAY);
			expectAuthError("wrong password", 401, () -> auth.startLogin("Bob", "password124"));
			expectAuthError("unknown user", 401, () -> auth.startLogin("nobody", "password123"));
			int before = mail.sent.size();
			AuthService.Challenge c = auth.startLogin("bob", "password123");
			assertEquals("login emails a code", before + 1, mail.sent.size());
			assertEquals("to the account's address", "Bob@example.com", mail.sent.get(before)[0]);
			AuthService.User bob = auth.verify(c.id, " " + mail.lastCode("Bob@example.com") + " ");
			assertEquals("login is case-insensitive and returns stored name", "Bob", bob.username);
			String token = auth.createSession(bob);
			assertEquals("token finds user", "Bob", auth.userForToken(token).username);
			assertEquals("unknown token", null, auth.userForToken("made-up-token"));
			assertEquals("missing token", null, auth.userForToken(null));
			auth.logout(token);
			assertEquals("token is invalid after logout", null, auth.userForToken(token));
		}
	}

	static void repeatedFailuresLockTheAccount(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			AuthService auth = newAuth(db, mail, new TestClock());
			signUp(auth, mail, "carol");
			for (int i = 0; i < AuthService.MAX_FAILURES; i++) {
				expectAuthError("failure " + (i + 1), 401, () -> auth.startLogin("carol", "wrong-password"));
			}
			expectAuthError("locked even with the right password", 429, () -> auth.startLogin("carol", "password123"));
		}
	}

	static void codesExpireAndCanBeResent(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			TestClock clock = new TestClock();
			AuthService auth = newAuth(db, mail, clock);
			AuthService.Challenge c = auth.startRegistration("hank", "hank@example.com", "password123");
			String first = mail.lastCode("hank@example.com");
			expectAuthError("resend too soon", 429, () -> auth.resend(c.id));
			expectAuthError("another sign-up to the same address too soon", 429,
					() -> auth.startRegistration("hank2", "HANK@example.com", "password123"));
			clock.advance(AuthService.RESEND_DELAY);
			auth.resend(c.id);
			String second = mail.lastCode("hank@example.com");
			if (!first.equals(second)) {
				expectAuthError("old code stops working after resend", 401, () -> auth.verify(c.id, first));
			}
			clock.advance(AuthService.CODE_LIFETIME.plusSeconds(1));
			expectAuthError("expired code", 400, () -> auth.verify(c.id, second));
			expectAuthError("unknown challenge", 400, () -> auth.verify("made-up", "123456"));

			AuthService.Challenge again = auth.startRegistration("hank", "hank@example.com", "password123");
			for (int i = 1; i < AuthService.MAX_SENDS; i++) {
				clock.advance(AuthService.RESEND_DELAY);
				auth.resend(again.id);
			}
			clock.advance(AuthService.RESEND_DELAY);
			expectAuthError("too many resends", 429, () -> auth.resend(again.id));
		}
	}

	static void wrongCodesAreLimited(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			TestClock clock = new TestClock();
			AuthService auth = newAuth(db, mail, clock);
			signUp(auth, mail, "ivy");
			clock.advance(AuthService.RESEND_DELAY);
			AuthService.Challenge c = auth.startLogin("ivy", "password123");
			String right = mail.lastCode("ivy@example.com");
			String wrong = right.equals("000000") ? "111111" : "000000";
			for (int i = 1; i < AuthService.MAX_CODE_ATTEMPTS; i++) {
				expectAuthError("wrong code " + i, 401, () -> auth.verify(c.id, wrong));
			}
			expectAuthError("last wrong code", 401, () -> auth.verify(c.id, wrong));
			expectAuthError("challenge cancelled after too many wrong codes", 400, () -> auth.verify(c.id, right));
			expectAuthError("wrong codes lock the account", 429, () -> auth.startLogin("ivy", "password123"));
		}
	}

	static void olderAccountsAddAnEmail(Path file) throws Exception {
		// A database from before two-factor login, with no email column.
		try (Connection old = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
				Statement s = old.createStatement()) {
			s.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE,"
					+ " password_hash TEXT NOT NULL, created_at TEXT NOT NULL)");
			s.execute("INSERT INTO users (username, password_hash, created_at) VALUES ('old', '"
					+ FAST_HASHER.hash("password123") + "', 'then')");
		}
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			TestClock clock = new TestClock();
			AuthService auth = newAuth(db, mail, clock);
			AuthService.Challenge c = auth.startLogin("old", "password123");
			assertEquals("older account is asked for an email", null, c.sentTo);
			assertEquals("nothing sent yet", 0, mail.sent.size());
			expectAuthError("can't verify before adding an email", 400, () -> auth.verify(c.id, "123456"));
			expectAuthError("bad email", 400, () -> auth.addEmail(c.id, "nope"));
			auth.addEmail(c.id, "old@example.com");
			assertEquals("logged in after verifying", "old", auth.verify(c.id, mail.lastCode("old@example.com")).username);
			clock.advance(AuthService.RESEND_DELAY);
			AuthService.Challenge next = auth.startLogin("old", "password123");
			assertEquals("email was saved", "o*d@example.com", next.sentTo);
			expectAuthError("can't replace a saved email", 400, () -> auth.addEmail(next.id, "evil@example.com"));
		}
	}

	static void mailFailureIsReported(Path file) throws Exception {
		try (Database db = new Database(file)) {
			AuthService auth = newAuth(db, (to, subject, body) -> {
				throw new IOException("mail server down");
			}, new TestClock());
			expectAuthError("email couldn't be sent", 502,
					() -> auth.startRegistration("judy", "judy@example.com", "password123"));
		}
	}

	static void savedCalculationsArePrivate(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			AuthService auth = newAuth(db, mail, new TestClock());
			CalculationStore store = new CalculationStore(db);
			AuthService.User dan = signUp(auth, mail, "dan");
			AuthService.User erin = signUp(auth, mail, "erin");
			CalculationStore.Saved first = store.save(dan.id, "First", "aluminum_regular=1", "{}", 0.05);
			CalculationStore.Saved second = store.save(dan.id, "Second", "aluminum_regular=2", "{}", 0.10);
			List<CalculationStore.Saved> list = store.list(dan.id);
			assertEquals("dan has two", 2, list.size());
			assertEquals("newest first", "Second", list.get(0).name);
			assertEquals("erin sees none", 0, store.list(erin.id).size());
			assertEquals("erin can't open dan's", null, store.get(erin.id, first.id));
			assertEquals("erin can't delete dan's", false, store.delete(erin.id, first.id));
			CalculationStore.Saved loaded = store.get(dan.id, second.id);
			assertEquals("inputs round-trip", "aluminum_regular=2", loaded.inputs);
			assertClose("total round-trips", 0.10, loaded.total);
			assertEquals("dan deletes his", true, store.delete(dan.id, first.id));
			assertEquals("one left", 1, store.list(dan.id).size());
		}
	}

	static void canonicalInputsKeepOnlyFilledCalculatorFields() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("pet_large", " 3 ");
		params.put("aluminum_regular", "10");
		params.put("aluminum_large_weight", "");
		params.put("name", "ignored");
		params.put("total", "999");
		assertEquals("canonical inputs", "aluminum_regular=10&pet_large=3", WebServer.canonicalInputs(params));
	}

	static void httpAccountFlow(Path file) throws Exception {
		try (Database db = new Database(file)) {
			RecordingMailer mail = new RecordingMailer();
			WebServer server = new WebServer("127.0.0.1", 0, WEB_ROOT, newAuth(db, mail, Clock.systemUTC()),
					new CalculationStore(db));
			server.start();
			try {
				Http http = new Http("http://127.0.0.1:" + server.getPort());

				HttpResponse<String> me = http.get("/api/me", null);
				assertEquals("me without login", "{\"accountsEnabled\":true,\"username\":null}", me.body());
				assertEquals("list without login", 401, http.get("/api/calculations", null).statusCode());

				HttpResponse<String> start = http.post("/api/register",
						"username=frank&email=frank%40example.com&password=password123", null);
				assertEquals("register step 1 status", 200, start.statusCode());
				assertEquals("register step 1 sets no cookie", true, start.headers().firstValue("Set-Cookie").isEmpty());
				assertEquals("register step 1 says where the code went", true,
						start.body().contains("\"sentTo\":\"f***k@example.com\""));
				String challenge = challengeOf(start);
				assertEquals("wrong code over HTTP", 401,
						http.post("/api/verify", "challenge=" + challenge + "&code=1234567", null).statusCode());
				HttpResponse<String> reg = http.post("/api/verify",
						"challenge=" + challenge + "&code=" + mail.lastCode("frank@example.com"), null);
				assertEquals("register step 2 status", 200, reg.statusCode());
				String setCookie = reg.headers().firstValue("Set-Cookie").orElse("");
				assertEquals("cookie is HttpOnly + SameSite", true,
						setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Strict"));
				String cookie = setCookie.substring(0, setCookie.indexOf(';'));

				assertEquals("me with login", "{\"accountsEnabled\":true,\"username\":\"frank\"}",
						http.get("/api/me", cookie).body());

				HttpResponse<String> saved = http.post("/api/calculations",
						"name=Saturday&aluminum_regular=10&total=999", cookie);
				assertEquals("save status", 201, saved.statusCode());
				assertEquals("server computes the total", true, saved.body().contains("\"total\":0.50"));
				String id = saved.body().replaceAll("^\\{\"id\":(\\d+),.*$", "$1");

				assertEquals("list includes it", true, http.get("/api/calculations", cookie).body().contains("\"Saturday\""));
				HttpResponse<String> opened = http.get("/api/calculations/" + id, cookie);
				assertEquals("open status", 200, opened.statusCode());
				assertEquals("open returns inputs", true, opened.body().contains("\"inputs\":\"aluminum_regular=10\""));
				assertEquals("open returns results", true, opened.body().contains("\"result\":{\"results\":["));

				assertEquals("blank name refused", 400, http.post("/api/calculations", "name=+&pet_regular=1", cookie).statusCode());
				assertEquals("bad count refused", 400,
						http.post("/api/calculations", "name=x&pet_regular=-1", cookie).statusCode());
				assertEquals("cross-site save refused", 403, http.send(HttpRequest.newBuilder(http.uri("/api/calculations"))
						.header("Cookie", cookie).header("Origin", "http://evil.example")
						.header("Content-Type", "application/x-www-form-urlencoded")
						.POST(HttpRequest.BodyPublishers.ofString("name=x&pet_regular=1"))).statusCode());

				HttpResponse<String> otherStart = http.post("/api/register",
						"username=gina&email=gina%40example.com&password=password123", null);
				HttpResponse<String> other = http.post("/api/verify",
						"challenge=" + challengeOf(otherStart) + "&code=" + mail.lastCode("gina@example.com"), null);
				String otherCookie = other.headers().firstValue("Set-Cookie").orElse("").split(";")[0];
				assertEquals("other user can't open it", 404, http.get("/api/calculations/" + id, otherCookie).statusCode());
				assertEquals("other user can't delete it", 404, http.delete("/api/calculations/" + id, otherCookie).statusCode());
				assertEquals("bad id", 404, http.get("/api/calculations/abc", cookie).statusCode());

				assertEquals("wrong password over HTTP", 401,
						http.post("/api/login", "username=frank&password=nope-nope", null).statusCode());
				HttpResponse<String> login = http.post("/api/login", "username=frank&password=password123", null);
				assertEquals("login step 1 over HTTP", 200, login.statusCode());
				assertEquals("password alone doesn't log in", true, login.headers().firstValue("Set-Cookie").isEmpty());
				assertEquals("resend too soon over HTTP", 429,
						http.post("/api/verify/resend", "challenge=" + challengeOf(login), null).statusCode());
				HttpResponse<String> loggedIn = http.post("/api/verify",
						"challenge=" + challengeOf(login) + "&code=" + mail.lastCode("frank@example.com"), null);
				assertEquals("login step 2 over HTTP", "{\"username\":\"frank\"}", loggedIn.body());

				assertEquals("owner deletes it", 204, http.delete("/api/calculations/" + id, cookie).statusCode());
				assertEquals("gone after delete", 404, http.get("/api/calculations/" + id, cookie).statusCode());

				HttpResponse<String> out = http.post("/api/logout", "", cookie);
				assertEquals("logout clears cookie", true, out.headers().firstValue("Set-Cookie").orElse("").contains("Max-Age=0"));
				assertEquals("session ended", 401, http.get("/api/calculations", cookie).statusCode());
			} finally {
				server.stop();
			}
		}
	}

	static void accountsCanBeTurnedOff() throws Exception {
		WebServer server = new WebServer("127.0.0.1", 0, WEB_ROOT);
		server.start();
		try {
			Http http = new Http("http://127.0.0.1:" + server.getPort());
			assertEquals("me reports disabled", "{\"accountsEnabled\":false,\"username\":null}",
					http.get("/api/me", null).body());
			assertEquals("login unavailable", 503, http.post("/api/login", "username=a&password=b", null).statusCode());
			assertEquals("calculator still works", 200, http.get("/api/calculate?pet_regular=1", null).statusCode());
		} finally {
			server.stop();
		}
	}

	static void smtpMailerSpeaksSmtp() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			List<String> received = Collections.synchronizedList(new ArrayList<>());
			Thread fake = new Thread(() -> {
				try (Socket s = listener.accept();
						BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
						Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)) {
					out.write("220 fake ready\r\n");
					out.flush();
					boolean inData = false;
					String line;
					while ((line = in.readLine()) != null) {
						received.add(line);
						String reply;
						if (inData) {
							if (!line.equals(".")) {
								continue;
							}
							inData = false;
							reply = "250 queued";
						} else if (line.startsWith("EHLO")) {
							reply = "250-fake\r\n250 8BITMIME";
						} else if (line.equals("DATA")) {
							inData = true;
							reply = "354 go ahead";
						} else if (line.equals("QUIT")) {
							out.write("221 bye\r\n");
							out.flush();
							return;
						} else {
							reply = "250 ok";
						}
						out.write(reply + "\r\n");
						out.flush();
					}
				} catch (IOException e) {
					received.add("ERROR " + e);
				}
			});
			fake.start();
			Properties props = new Properties();
			props.setProperty("smtp.host", "127.0.0.1");
			props.setProperty("smtp.port", String.valueOf(listener.getLocalPort()));
			props.setProperty("smtp.security", "none");
			props.setProperty("mail.from", "app@example.com");
			SmtpMailer.fromProperties(props).send("kim@example.com", "Your code", "Code 123456\n.hidden line\nbye");
			fake.join(5000);
			assertEquals("sender", true, received.contains("MAIL FROM:<app@example.com>"));
			assertEquals("recipient", true, received.contains("RCPT TO:<kim@example.com>"));
			assertEquals("subject header", true, received.contains("Subject: Your code"));
			assertEquals("body", true, received.contains("Code 123456"));
			assertEquals("leading dot is doubled", true, received.contains("..hidden line"));
			assertEquals("ends politely", "QUIT", received.get(received.size() - 1));
		}

		Properties insecure = new Properties();
		insecure.setProperty("smtp.host", "smtp.example.com");
		insecure.setProperty("smtp.security", "none");
		insecure.setProperty("smtp.username", "me@example.com");
		boolean refused;
		try {
			SmtpMailer.fromProperties(insecure);
			refused = false;
		} catch (IllegalArgumentException e) {
			refused = true;
		}
		assertEquals("won't send a password unencrypted", true, refused);
	}

	private static String challengeOf(HttpResponse<String> response) {
		Matcher m = Pattern.compile("\"challenge\":\"([^\"]+)\"").matcher(response.body());
		return m.find() ? m.group(1) : "missing";
	}

	private static final class Http {
		private final HttpClient client = HttpClient.newHttpClient();
		private final String base;

		Http(String base) {
			this.base = base;
		}

		URI uri(String path) {
			return URI.create(base + path);
		}

		HttpResponse<String> get(String path, String cookie) throws IOException, InterruptedException {
			return send(withCookie(HttpRequest.newBuilder(uri(path)).GET(), cookie));
		}

		HttpResponse<String> post(String path, String form, String cookie) throws IOException, InterruptedException {
			return send(withCookie(HttpRequest.newBuilder(uri(path))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(form)), cookie));
		}

		HttpResponse<String> delete(String path, String cookie) throws IOException, InterruptedException {
			return send(withCookie(HttpRequest.newBuilder(uri(path)).DELETE(), cookie));
		}

		HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}

		private static HttpRequest.Builder withCookie(HttpRequest.Builder b, String cookie) {
			return cookie == null ? b : b.header("Cookie", cookie);
		}
	}

	private interface AuthCall {
		void run() throws Exception;
	}

	private static void expectAuthError(String name, int status, AuthCall call) {
		try {
			call.run();
			fail(name + ": expected AuthException " + status);
		} catch (AuthService.AuthException e) {
			assertEquals(name, status, e.status);
		} catch (Exception e) {
			fail(name + ": unexpected " + e);
		}
	}

	private static void assertClose(String name, double expected, double actual) {
		if (Math.abs(expected - actual) < 1e-9) {
			passed++;
		} else {
			fail(name + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertEquals(String name, Object expected, Object actual) {
		if (expected == null ? actual == null : expected.equals(actual)) {
			passed++;
		} else {
			fail(name + ": expected " + expected + " but was " + actual);
		}
	}

	private static void fail(String message) {
		failures++;
		System.out.println("FAIL " + message);
	}

	private static void deleteRecursively(Path dir) throws IOException {
		try (Stream<Path> paths = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
				Files.deleteIfExists(p);
			}
		}
	}
}
