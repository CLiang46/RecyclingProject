package com.recycleproj;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small HTTP server built on the JDK's com.sun.net.httpserver.
 *
 * GET  /                -> the calculator web page
 * GET  /api/materials   -> materials, rates and estimated container weights
 * GET|POST /api/calculate -> payout for a load; parameters per material key:
 *        {key}_regular, {key}_large, {key}_nonrefundable (counts),
 *        {key}_regular_weight, {key}_large_weight (pounds, optional)
 */
public class WebServer {
	/** Representative fluid-ounce sizes used when the web form only supplies regular/large counts. */
	static final int REGULAR_SIZE_OZ = 12;
	static final int LARGE_SIZE_OZ = Recycle.LARGE_CONTAINER_OZ;
	static final int MAX_QUANTITY = 1_000_000;
	private static final int MAX_BODY_BYTES = 64 * 1024;

	private final HttpServer server;
	private final Path pageFile;

	public WebServer(String host, int port, Path webRoot) throws IOException {
		this.pageFile = webRoot.resolve("MainPage.html");
		if (!Files.isRegularFile(pageFile)) {
			throw new IOException("Cannot find " + pageFile.toAbsolutePath()
					+ " (run from the repository root or pass --webroot)");
		}
		this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext("/", this::handlePage);
		server.createContext("/api/materials", this::handleMaterials);
		server.createContext("/api/calculate", this::handleCalculate);
	}

	public void start() {
		server.start();
	}

	public void stop() {
		server.stop(0);
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
					.append(",\"materials\":[");
			Material[] all = Material.values();
			for (int i = 0; i < all.length; i++) {
				Material m = all[i];
				if (i > 0) {
					sb.append(',');
				}
				sb.append("{\"key\":").append(quote(m.getKey()))
						.append(",\"name\":").append(quote(m.getDisplayName()))
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
