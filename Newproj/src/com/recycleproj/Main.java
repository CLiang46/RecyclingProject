package com.recycleproj;

import java.nio.file.Path;

/**
 * Starts the Recycling Buyback Calculator web server.
 *
 * Usage: java -cp Newproj/build com.recycleproj.Main [--port 8080] [--host 127.0.0.1]
 *        [--webroot BuybackCalculatorFrontend/src/main/webapp]
 */
public class Main {
	public static void main(String[] args) throws Exception {
		int port = 8080;
		String host = "127.0.0.1";
		Path webRoot = Path.of("BuybackCalculatorFrontend", "src", "main", "webapp");

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--help") || arg.equals("-h")) {
				System.out.println("Options: --port <n> (default 8080), --host <addr> (default 127.0.0.1), --webroot <dir>");
				return;
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
			default:
				System.err.println("Unknown option " + arg);
				System.exit(2);
			}
		}

		WebServer server = new WebServer(host, port, webRoot);
		server.start();
		System.out.println("Recycling Buyback Calculator running at http://" + host + ":" + server.getPort() + "/");
		System.out.println("Press Ctrl+C to stop.");
	}
}
