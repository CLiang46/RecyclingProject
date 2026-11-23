package com.recycleproj;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Starts the Recycling Buyback Calculator web server.
 *
 * Usage: java -cp "Newproj/build:lib/*" com.recycleproj.Main [--port 8080] [--host 127.0.0.1]
 *        [--webroot BuybackCalculatorFrontend/src/main/webapp] [--db data/recycling.db] [--no-db]
 *        [--mail-config mail.properties]
 */
public class Main {
	public static void main(String[] args) throws Exception {
		int port = 8080;
		String host = "127.0.0.1";
		Path webRoot = Path.of("BuybackCalculatorFrontend", "src", "main", "webapp");
		Path dbFile = Path.of("data", "recycling.db");
		Path mailConfig = Path.of("mail.properties");
		boolean useDb = true;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--help") || arg.equals("-h")) {
				System.out.println("Options: --port <n> (default 8080), --host <addr> (default 127.0.0.1), --webroot <dir>,");
				System.out.println("         --db <file> (default data/recycling.db), --no-db (calculator only, no accounts),");
				System.out.println("         --mail-config <file> (SMTP settings for verification codes; default mail.properties)");
				return;
			}
			if (arg.equals("--no-db")) {
				useDb = false;
				continue;
			}
			if (i + 1 >= args.length) {
				System.err.println("Missing value for " + arg);
				System.exit(2);
			}
			String value = args[++i];
			switch (arg) {
			case "--port":
				port = Integer.parseInt(value);
				break;
			case "--host":
				host = value;
				break;
			case "--webroot":
				webRoot = Path.of(value);
				break;
			case "--db":
				dbFile = Path.of(value);
				break;
			case "--mail-config":
				mailConfig = Path.of(value);
				break;
			default:
				System.err.println("Unknown option " + arg);
				System.exit(2);
			}
		}

		Database db = null;
		if (useDb) {
			try {
				db = new Database(dbFile);
				System.out.println("Saving accounts and calculations in " + dbFile.toAbsolutePath());
			} catch (SQLException e) {
				System.err.println("WARNING: " + e.getMessage());
				System.err.println("Starting without accounts; the calculator still works but results can't be saved.");
			}
		}

		Mailer mailer = null;
		if (db != null) {
			mailer = loadMailer(mailConfig);
		}

		WebServer server = new WebServer(host, port, webRoot, db, mailer);
		server.start();
		System.out.println("Recycling Buyback Calculator running at http://" + host + ":" + server.getPort() + "/");
		System.out.println("Press Ctrl+C to stop.");
	}

	private static Mailer loadMailer(Path config) throws IOException {
		if (!Files.isRegularFile(config)) {
			System.out.println("NOTE: " + config + " not found, so verification codes will be printed here instead of "
					+ "emailed. See mail.properties.example to set up email.");
			return Mailer.console();
		}
		Properties props = new Properties();
		try (Reader in = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
			props.load(in);
		}
		try {
			SmtpMailer mailer = SmtpMailer.fromProperties(props);
			System.out.println("Emailing verification codes through " + mailer.getHost());
			return mailer;
		} catch (IllegalArgumentException e) {
			System.err.println("Problem in " + config + ": " + e.getMessage());
			System.exit(2);
			return null;
		}
	}
}
