package com.recycleproj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Sends plain-text email over SMTP using only the JDK.
 * Supports implicit TLS (usually port 465) and STARTTLS (usually port 587). Unencrypted connections are allowed only
 * without a password, for local test mail servers.
 */
public class SmtpMailer implements Mailer {
	public enum Security { SSL, STARTTLS, NONE }

	private static final int TIMEOUT_MS = 20_000;
	private static final String SENDER_NAME = "Recycling Buyback Calculator";

	private final String host;
	private final int port;
	private final Security security;
	private final String username;
	private final String password;
	private final String from;

	public SmtpMailer(String host, int port, Security security, String username, String password, String from) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("smtp.host is required");
		}
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("smtp.port must be 1-65535");
		}
		if (from == null || !AuthService.isValidEmail(from)) {
			throw new IllegalArgumentException("mail.from must be an email address");
		}
		if (security == Security.NONE && username != null) {
			throw new IllegalArgumentException(
					"Refusing to send the SMTP password without encryption; set smtp.security to ssl or starttls");
		}
		this.host = host.trim();
		this.port = port;
		this.security = security;
		this.username = username;
		this.password = password == null ? "" : password;
		this.from = from;
	}

	/**
	 * Reads smtp.host, smtp.port, smtp.security (ssl, starttls or none; default starttls), smtp.username,
	 * smtp.password and mail.from (defaults to smtp.username).
	 */
	public static SmtpMailer fromProperties(Properties p) {
		Security security;
		try {
			security = Security.valueOf(p.getProperty("smtp.security", "starttls").trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("smtp.security must be ssl, starttls or none");
		}
		int defaultPort = security == Security.SSL ? 465 : security == Security.STARTTLS ? 587 : 25;
		int port;
		try {
			port = Integer.parseInt(p.getProperty("smtp.port", String.valueOf(defaultPort)).trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("smtp.port must be a number");
		}
		String username = blankToNull(p.getProperty("smtp.username"));
		String from = blankToNull(p.getProperty("mail.from"));
		return new SmtpMailer(p.getProperty("smtp.host"), port, security, username, p.getProperty("smtp.password"),
				from == null ? username : from.trim());
	}

	public String getHost() {
		return host;
	}

	@Override
	public void send(String to, String subject, String body) throws IOException {
		if (!AuthService.isValidEmail(to) || subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
			throw new IOException("Invalid recipient or subject");
		}
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
			socket.setSoTimeout(TIMEOUT_MS);
			if (security == Security.SSL) {
				socket = startTls(socket);
			}
			Conversation c = new Conversation(socket);
			c.expect(220);
			c.command("EHLO localhost", 250);
			if (security == Security.STARTTLS) {
				c.command("STARTTLS", 220);
				socket = startTls(socket);
				c = new Conversation(socket);
				c.command("EHLO localhost", 250);
			}
			if (username != null) {
				String token = Base64.getEncoder()
						.encodeToString(("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8));
				c.command("AUTH PLAIN " + token, 235);
			}
			c.command("MAIL FROM:<" + from + ">", 250);
			c.command("RCPT TO:<" + to + ">", 250, 251);
			c.command("DATA", 354);
			c.data(message(to, subject, body));
			try {
				c.command("QUIT", 221);
			} catch (IOException ignored) {
				// The email was accepted; some servers just hang up.
			}
		} finally {
			socket.close();
		}
	}

	private String message(String to, String subject, String body) {
		String domain = from.substring(from.indexOf('@') + 1);
		return "Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\n"
				+ "From: " + SENDER_NAME + " <" + from + ">\n"
				+ "To: <" + to + ">\n"
				+ "Subject: " + subject + "\n"
				+ "Message-ID: <" + UUID.randomUUID() + "@" + domain + ">\n"
				+ "MIME-Version: 1.0\n"
				+ "Content-Type: text/plain; charset=UTF-8\n"
				+ "Content-Transfer-Encoding: 8bit\n"
				+ "\n"
				+ body;
	}

	private SSLSocket startTls(Socket plain) throws IOException {
		SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
		SSLParameters params = ssl.getSSLParameters();
		// Check that the certificate belongs to the mail server, not just that it's signed by someone trusted.
		params.setEndpointIdentificationAlgorithm("HTTPS");
		ssl.setSSLParameters(params);
		ssl.startHandshake();
		return ssl;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	/** One request/reply exchange stream with the server. */
	private static final class Conversation {
		private final BufferedReader in;
		private final Writer out;

		Conversation(Socket socket) throws IOException {
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
		}

		void command(String line, int... ok) throws IOException {
			out.write(line + "\r\n");
			out.flush();
			expect(ok);
		}

		/** Sends the message, doubling any leading '.' so a line with a single dot can't end it early. */
		void data(String message) throws IOException {
			for (String line : message.replace("\r", "").split("\n", -1)) {
				out.write(line.startsWith(".") ? "." + line : line);
				out.write("\r\n");
			}
			out.write(".\r\n");
			out.flush();
			expect(250);
		}

		void expect(int... ok) throws IOException {
			StringBuilder reply = new StringBuilder();
			String line;
			do {
				line = in.readLine();
				if (line == null) {
					throw new IOException("Mail server closed the connection");
				}
				reply.append(line).append(' ');
			} while (line.length() > 3 && line.charAt(3) == '-');
			int code;
			try {
				code = Integer.parseInt(line.substring(0, Math.min(3, line.length())));
			} catch (NumberFormatException e) {
				throw new IOException("Unexpected reply from mail server: " + line);
			}
			for (int c : ok) {
				if (c == code) {
					return;
				}
			}
			throw new IOException("Mail server replied: " + reply.toString().trim());
		}
	}
}
