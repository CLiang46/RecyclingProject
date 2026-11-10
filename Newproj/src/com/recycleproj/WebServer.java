package com.recycleproj;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small HTTP server built on the JDK's com.sun.net.httpserver.
 *
 * GET  /                -> the calculator web page
 * GET  /api/materials   -> materials, rates and estimated container weights
 * GET|POST /api/calculate -> payout for a load; parameters per material key:
 *        {key}_regular, {key}_large, {key}_nonrefundable (counts),
 *        {key}_regular_weight, {key}_large_weight (pounds, optional)
 *
 * Accounts (only when started with a database):
 * POST /api/register, /api/login (username, password), /api/logout; GET /api/me
 * GET  /api/calculations            -> the logged-in user's saved calculations
 * POST /api/calculations            -> save: name plus the same parameters as /api/calculate
 * GET|DELETE /api/calculations/{id} -> open or delete one saved calculation
 */
public class WebServer {
	/** Representative fluid-ounce sizes used when the web form only supplies regular/large counts. */
	static final int REGULAR_SIZE_OZ = 12;
	static final int LARGE_SIZE_OZ = Recycle.LARGE_CONTAINER_OZ;
	static final int MAX_QUANTITY = 1_000_000;
	private static final int MAX_BODY_BYTES = 64 * 1024;

	static final String SESSION_COOKIE = "session";

	private final HttpServer server;
	private final Path pageFile;
	private final AuthService auth;
	private final CalculationStore store;

	public WebServer(String host, int port, Path webRoot) throws IOException {
		this(host, port, webRoot, (Database) null);
	}

	/** @param db database for accounts and saved calculations, or null to run the calculator only */
	public WebServer(String host, int port, Path webRoot, Database db) throws IOException {
		this(host, port, webRoot, db == null ? null : new AuthService(db), db == null ? null : new CalculationStore(db));
	}

	WebServer(String host, int port, Path webRoot, AuthService auth, CalculationStore store) throws IOException {
		this.auth = auth;
		this.store = store;
		this.pageFile = webRoot.resolve("MainPage.html");
		if (!Files.isRegularFile(pageFile)) {
			throw new IOException("Cannot find " + pageFile.toAbsolutePath()
					+ " (run from the repository root or pass --webroot)");
		}
		this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext("/", this::handlePage);
		server.createContext("/api/materials", this::handleMaterials);
		server.createContext("/api/calculate", this::handleCalculate);
		server.createContext("/api/me", ex -> handleAccount(ex, this::me));
		server.createContext("/api/register", ex -> handleAccount(ex, this::register));
		server.createContext("/api/login", ex -> handleAccount(ex, this::login));
		server.createContext("/api/logout", ex -> handleAccount(ex, this::logout));
		server.createContext("/api/calculations", ex -> handleAccount(ex, this::calculations));
		// Password hashing is deliberately slow, so one login shouldn't block every other request.
		server.setExecutor(Executors.newFixedThreadPool(4));
	}

	public void start() {
		server.start();
	}

	public void stop() {
		server.stop(0);
		if (server.getExecutor() instanceof ExecutorService) {
			((ExecutorService) server.getExecutor()).shutdown();
		}
	}

	public int getPort() {
		return server.getAddress().getPort();
	}

	private void handlePage(HttpExchange ex) throws IOException {
		try {
			String path = ex.getRequestURI().getPath();
			if (!path.equals("/") && !path.equals("/MainPage.html")) {
				sendJson(ex, 404, "{\"error\":\"Not found\"}");
				return;
			}
			if (!ex.getRequestMethod().equals("GET")) {
				sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
				return;
			}
			send(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(pageFile));
		} finally {
			ex.close();
		}
	}

	private void handleMaterials(HttpExchange ex) throws IOException {
		try {
			if (!ex.getRequestMethod().equals("GET")) {
				sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
				return;
			}
			StringBuilder sb = new StringBuilder();
			sb.append("{\"countLimit\":").append(BuybackCalculator.COUNT_LIMIT)
					.append(",\"largeContainerOz\":").append(Recycle.LARGE_CONTAINER_OZ)
					.append(",\"regularCrv\":").append(money(Recycle.REGULAR_CRV))
					.append(",\"largeCrv\":").append(money(Recycle.LARGE_CRV))
					.append(",\"categories\":[");
			Material.Category[] categories = Material.Category.values();
			for (int i = 0; i < categories.length; i++) {
				if (i > 0) {
					sb.append(',');
				}
				sb.append("{\"key\":").append(quote(categories[i].getKey()))
						.append(",\"name\":").append(quote(categories[i].getDisplayName())).append('}');
			}
			sb.append("],\"materials\":[");
			Material[] all = Material.values();
			for (int i = 0; i < all.length; i++) {
				Material m = all[i];
				if (i > 0) {
					sb.append(',');
				}
				sb.append("{\"key\":").append(quote(m.getKey()))
						.append(",\"name\":").append(quote(m.getDisplayName()))
						.append(",\"category\":").append(quote(m.getCategory().getKey()))
						.append(",\"perPoundRate\":").append(num(m.getPerPoundRate(), 4))
						.append(",\"regularWeightOz\":").append(num(m.getEmptyWeightOz(false), 2))
						.append(",\"largeWeightOz\":").append(num(m.getEmptyWeightOz(true), 2))
						.append('}');
			}
			sb.append("]}");
			sendJson(ex, 200, sb.toString());
		} finally {
			ex.close();
		}
	}

	private void handleCalculate(HttpExchange ex) throws IOException {
		try {
			String query;
			if (ex.getRequestMethod().equals("GET")) {
				query = ex.getRequestURI().getRawQuery();
			} else if (ex.getRequestMethod().equals("POST")) {
				query = readBody(ex);
				if (query == null) {
					sendJson(ex, 413, "{\"error\":\"Request body too large\"}");
					return;
				}
			} else {
				sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
				return;
			}
			try {
				sendJson(ex, 200, toJson(calculate(parseParams(query))));
			} catch (IllegalArgumentException e) {
				sendJson(ex, 400, "{\"error\":" + quote(e.getMessage()) + "}");
			}
		} finally {
			ex.close();
		}
	}

	/** An error response whose message is safe to show to the user. */
	static final class HttpError extends Exception {
		private static final long serialVersionUID = 1L;
		final int status;

		HttpError(int status, String message) {
			super(message);
			this.status = status;
		}
	}

	private interface AccountHandler {
		void handle(HttpExchange ex) throws IOException, SQLException, HttpError, AuthService.AuthException;
	}

	private void handleAccount(HttpExchange ex, AccountHandler handler) throws IOException {
		try {
			String method = ex.getRequestMethod();
			if (!method.equals("GET") && !method.equals("HEAD") && !sameOrigin(ex)) {
				throw new HttpError(403, "Cross-site request refused");
			}
			if (auth == null && !ex.getRequestURI().getPath().equals("/api/me")) {
				throw new HttpError(503, "Accounts are turned off because the server was started without a database");
			}
			handler.handle(ex);
		} catch (HttpError e) {
			sendJson(ex, e.status, "{\"error\":" + quote(e.getMessage()) + "}");
		} catch (AuthService.AuthException e) {
			sendJson(ex, e.status, "{\"error\":" + quote(e.getMessage()) + "}");
		} catch (IllegalArgumentException e) {
			sendJson(ex, 400, "{\"error\":" + quote(e.getMessage()) + "}");
		} catch (SQLException e) {
			System.err.println("Database error: " + e);
			sendJson(ex, 500, "{\"error\":\"Database error\"}");
		} finally {
			ex.close();
		}
	}

	/** Browsers send Origin on POST/DELETE; reject requests coming from another site's page. */
	private static boolean sameOrigin(HttpExchange ex) {
		String origin = ex.getRequestHeaders().getFirst("Origin");
		if (origin == null) {
			return true;
		}
		String host = ex.getRequestHeaders().getFirst("Host");
		return host != null && origin.equals("http://" + host);
	}

	private void me(HttpExchange ex) throws IOException, SQLException, HttpError {
		requireMethod(ex, "GET");
		if (auth == null) {
			sendJson(ex, 200, "{\"accountsEnabled\":false,\"username\":null}");
			return;
		}
		AuthService.User user = auth.userForToken(sessionToken(ex));
		sendJson(ex, 200, "{\"accountsEnabled\":true,\"username\":" + (user == null ? "null" : quote(user.username)) + "}");
	}

	private void register(HttpExchange ex) throws IOException, SQLException, HttpError, AuthService.AuthException {
		requireMethod(ex, "POST");
		Map<String, String> form = parseParams(readForm(ex));
		AuthService.User user = auth.register(form.get("username"), form.get("password"));
		startSession(ex, user, 201);
	}

	private void login(HttpExchange ex) throws IOException, SQLException, HttpError, AuthService.AuthException {
		requireMethod(ex, "POST");
		Map<String, String> form = parseParams(readForm(ex));
		AuthService.User user = auth.login(form.get("username"), form.get("password"));
		startSession(ex, user, 200);
	}

	private void startSession(HttpExchange ex, AuthService.User user, int status) throws IOException, SQLException {
		String token = auth.createSession(user);
		ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
				+ AuthService.SESSION_LENGTH.getSeconds());
		sendJson(ex, status, "{\"username\":" + quote(user.username) + "}");
	}

	private void logout(HttpExchange ex) throws IOException, SQLException, HttpError {
		requireMethod(ex, "POST");
		auth.logout(sessionToken(ex));
		ex.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
		sendJson(ex, 200, "{}");
	}

	private void calculations(HttpExchange ex) throws IOException, SQLException, HttpError {
		AuthService.User user = auth.userForToken(sessionToken(ex));
		if (user == null) {
			throw new HttpError(401, "Log in to save and view calculations");
		}
		String path = ex.getRequestURI().getPath();
		String method = ex.getRequestMethod();
		if (path.equals("/api/calculations") || path.equals("/api/calculations/")) {
			if (method.equals("GET")) {
				listCalculations(ex, user);
			} else if (method.equals("POST")) {
				saveCalculation(ex, user);
			} else {
				throw new HttpError(405, "Method not allowed");
			}
			return;
		}
		String prefix = "/api/calculations/";
		String idText = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
		if (!idText.matches("[0-9]{1,18}")) {
			throw new HttpError(404, "Not found");
		}
		long id = Long.parseLong(idText);
		if (method.equals("GET")) {
			CalculationStore.Saved saved = store.get(user.id, id);
			if (saved == null) {
				throw new HttpError(404, "Saved calculation not found");
			}
			sendJson(ex, 200, "{\"id\":" + saved.id + ",\"name\":" + quote(saved.name) + ",\"createdAt\":"
					+ quote(saved.createdAt) + ",\"total\":" + money(saved.total) + ",\"inputs\":" + quote(saved.inputs)
					+ ",\"result\":" + saved.resultsJson + "}");
		} else if (method.equals("DELETE")) {
			if (!store.delete(user.id, id)) {
				throw new HttpError(404, "Saved calculation not found");
			}
			ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
			ex.sendResponseHeaders(204, -1);
		} else {
			throw new HttpError(405, "Method not allowed");
		}
	}

	private void listCalculations(HttpExchange ex, AuthService.User user) throws IOException, SQLException {
		StringBuilder sb = new StringBuilder("{\"calculations\":[");
		List<CalculationStore.Saved> all = store.list(user.id);
		for (int i = 0; i < all.size(); i++) {
			CalculationStore.Saved s = all.get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"id\":").append(s.id)
					.append(",\"name\":").append(quote(s.name))
					.append(",\"createdAt\":").append(quote(s.createdAt))
					.append(",\"total\":").append(money(s.total))
					.append('}');
		}
		sendJson(ex, 200, sb.append("]}").toString());
	}

	private void saveCalculation(HttpExchange ex, AuthService.User user) throws IOException, SQLException, HttpError {
		Map<String, String> params = parseParams(readForm(ex));
		String name = params.getOrDefault("name", "").trim();
		if (name.isEmpty() || name.length() > CalculationStore.MAX_NAME_LENGTH) {
			throw new HttpError(400, "Name must be 1-" + CalculationStore.MAX_NAME_LENGTH + " characters");
		}
		// Recalculate on the server so saved results can't be tampered with by the browser.
		String inputs = canonicalInputs(params);
		BuybackCalculator calc = calculate(params);
		CalculationStore.Saved saved = store.save(user.id, name, inputs, toJson(calc), calc.total());
		sendJson(ex, 201, "{\"id\":" + saved.id + ",\"name\":" + quote(saved.name) + ",\"createdAt\":"
				+ quote(saved.createdAt) + ",\"total\":" + money(saved.total) + "}");
	}

	/** The calculator parameters that were filled in, in a fixed order, form-encoded. */
	static String canonicalInputs(Map<String, String> params) {
		StringBuilder sb = new StringBuilder();
		for (Material m : Material.values()) {
			for (String suffix : INPUT_SUFFIXES) {
				String name = m.getKey() + suffix;
				String value = params.get(name);
				if (value == null || value.isBlank()) {
					continue;
				}
				if (sb.length() > 0) {
					sb.append('&');
				}
				sb.append(name).append('=').append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
			}
		}
		return sb.toString();
	}

	private static final String[] INPUT_SUFFIXES = { "_regular", "_large", "_nonrefundable", "_regular_weight",
			"_large_weight" };

	private static void requireMethod(HttpExchange ex, String method) throws HttpError {
		if (!ex.getRequestMethod().equals(method)) {
			throw new HttpError(405, "Method not allowed");
		}
	}

	private static String readForm(HttpExchange ex) throws IOException, HttpError {
		String body = readBody(ex);
		if (body == null) {
			throw new HttpError(413, "Request body too large");
		}
		return body;
	}

	static String sessionToken(HttpExchange ex) {
		List<String> headers = ex.getRequestHeaders().get("Cookie");
		if (headers == null) {
			return null;
		}
		for (String header : headers) {
			for (String part : header.split(";")) {
				String p = part.trim();
				if (p.startsWith(SESSION_COOKIE + "=")) {
					return p.substring(SESSION_COOKIE.length() + 1);
				}
			}
		}
		return null;
	}

	static BuybackCalculator calculate(Map<String, String> params) {
		BuybackCalculator calc = new BuybackCalculator();
		for (Material m : Material.values()) {
			String k = m.getKey();
			calc.add(m.newContainer(REGULAR_SIZE_OZ, true), quantity(params, k + "_regular"));
			calc.add(m.newContainer(LARGE_SIZE_OZ, true), quantity(params, k + "_large"));
			calc.add(m.newContainer(REGULAR_SIZE_OZ, false), quantity(params, k + "_nonrefundable"));
			applyWeight(calc, m, false, params, k + "_regular_weight");
			applyWeight(calc, m, true, params, k + "_large_weight");
		}
		return calc;
	}

	private static void applyWeight(BuybackCalculator calc, Material m, boolean large, Map<String, String> params,
			String name) {
		String weight = params.get(name);
		if (weight == null || weight.isBlank()) {
			return;
		}
		double pounds;
		try {
			pounds = Double.parseDouble(weight.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(name + " must be a number");
		}
		if (!(pounds >= 0) || pounds > MAX_QUANTITY) {
			throw new IllegalArgumentException(name + " must be between 0 and " + MAX_QUANTITY);
		}
		calc.setActualWeightPounds(m, large, pounds);
	}

	private static int quantity(Map<String, String> params, String name) {
		String v = params.get(name);
		if (v == null || v.isBlank()) {
			return 0;
		}
		try {
			int q = Integer.parseInt(v.trim());
			if (q < 0 || q > MAX_QUANTITY) {
				throw new NumberFormatException();
			}
			return q;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(name + " must be a whole number between 0 and " + MAX_QUANTITY);
		}
	}

	static String toJson(BuybackCalculator calc) {
		List<BuybackCalculator.MaterialResult> results = calc.calculate();
		double total = 0;
		StringBuilder sb = new StringBuilder("{\"results\":[");
		for (int i = 0; i < results.size(); i++) {
			BuybackCalculator.MaterialResult r = results.get(i);
			total += r.payout;
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"key\":").append(quote(r.material.getKey()))
					.append(",\"name\":").append(quote(r.material.getDisplayName()))
					.append(",\"size\":").append(quote(r.large ? "large" : "regular"))
					.append(",\"count\":").append(r.count)
					.append(",\"nonRefundableCount\":").append(r.nonRefundableCount)
					.append(",\"weightPounds\":").append(num(r.weightOz / 16, 3))
					.append(",\"weightEstimated\":").append(r.weightEstimated)
					.append(",\"countValue\":").append(money(r.countValue))
					.append(",\"weightValue\":").append(money(r.weightValue))
					.append(",\"method\":").append(quote(r.paidByWeight ? "weight" : "count"))
					.append(",\"payout\":").append(money(r.payout))
					.append('}');
		}
		sb.append("],\"total\":").append(money(total)).append('}');
		return sb.toString();
	}

	static Map<String, String> parseParams(String raw) {
		Map<String, String> params = new HashMap<>();
		if (raw == null || raw.isEmpty()) {
			return params;
		}
		for (String pair : raw.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String name = eq < 0 ? pair : pair.substring(0, eq);
			String value = eq < 0 ? "" : pair.substring(eq + 1);
			params.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return params;
	}

	/** Returns the body as a string, or null if it exceeds the size limit. */
	private static String readBody(HttpExchange ex) throws IOException {
		try (InputStream in = ex.getRequestBody()) {
			byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
			if (body.length > MAX_BODY_BYTES) {
				return null;
			}
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
		send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
	}

	private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
		ex.getResponseHeaders().set("Content-Type", contentType);
		ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		ex.sendResponseHeaders(status, body.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(body);
		}
	}

	private static String money(double value) {
		return num(value, 2);
	}

	private static String num(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	private static String quote(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			default:
				if (c < 0x20 || c == '<' || c == '>') {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}
}
