package com.recycleproj;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
			savedCalculationsArePrivate(dir.resolve("store.db"));
			canonicalInputsKeepOnlyFilledCalculatorFields();
			httpAccountFlow(dir.resolve("http.db"));
			accountsCanBeTurnedOff();
		} finally {
			deleteRecursively(dir);
		}

		System.out.println(passed + " passed, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
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
			AuthService auth = new AuthService(db, FAST_HASHER);
			expectAuthError("short username", 400, () -> auth.register("ab", "password123"));
			expectAuthError("username with spaces", 400, () -> auth.register("bad name", "password123"));
			expectAuthError("short password", 400, () -> auth.register("alice", "short"));
			AuthService.User alice = auth.register("alice", "password123");
			assertEquals("registered name", "alice", alice.username);
			expectAuthError("duplicate username, any case", 409, () -> auth.register("ALICE", "password123"));
		}
	}

	static void loginCreatesAndEndsSessions(Path file) throws Exception {
		try (Database db = new Database(file)) {
			AuthService auth = new AuthService(db, FAST_HASHER);
			auth.register("Bob", "password123");
			expectAuthError("wrong password", 401, () -> auth.login("Bob", "password124"));
			expectAuthError("unknown user", 401, () -> auth.login("nobody", "password123"));
			AuthService.User bob = auth.login("bob", "password123");
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
			AuthService auth = new AuthService(db, FAST_HASHER);
			auth.register("carol", "password123");
			for (int i = 0; i < AuthService.MAX_FAILURES; i++) {
				expectAuthError("failure " + (i + 1), 401, () -> auth.login("carol", "wrong-password"));
			}
			expectAuthError("locked even with the right password", 429, () -> auth.login("carol", "password123"));
		}
	}

	static void savedCalculationsArePrivate(Path file) throws Exception {
		try (Database db = new Database(file)) {
			AuthService auth = new AuthService(db, FAST_HASHER);
			CalculationStore store = new CalculationStore(db);
			AuthService.User dan = auth.register("dan", "password123");
			AuthService.User erin = auth.register("erin", "password123");
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
			WebServer server = new WebServer("127.0.0.1", 0, WEB_ROOT, new AuthService(db, FAST_HASHER),
					new CalculationStore(db));
			server.start();
			try {
				Http http = new Http("http://127.0.0.1:" + server.getPort());

				HttpResponse<String> me = http.get("/api/me", null);
				assertEquals("me without login", "{\"accountsEnabled\":true,\"username\":null}", me.body());
				assertEquals("list without login", 401, http.get("/api/calculations", null).statusCode());

				HttpResponse<String> reg = http.post("/api/register", "username=frank&password=password123", null);
				assertEquals("register status", 201, reg.statusCode());
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

				HttpResponse<String> other = http.post("/api/register", "username=gina&password=password123", null);
				String otherCookie = other.headers().firstValue("Set-Cookie").orElse("").split(";")[0];
				assertEquals("other user can't open it", 404, http.get("/api/calculations/" + id, otherCookie).statusCode());
				assertEquals("other user can't delete it", 404, http.delete("/api/calculations/" + id, otherCookie).statusCode());
				assertEquals("bad id", 404, http.get("/api/calculations/abc", cookie).statusCode());

				assertEquals("wrong password over HTTP", 401,
						http.post("/api/login", "username=frank&password=nope-nope", null).statusCode());
				assertEquals("login over HTTP", 200,
						http.post("/api/login", "username=frank&password=password123", null).statusCode());

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
