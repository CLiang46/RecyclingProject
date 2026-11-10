package com.recycleproj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite database holding user accounts, login sessions and saved calculations.
 * Requires the sqlite-jdbc driver on the classpath at runtime (the run scripts download it into lib/).
 *
 * A single connection is shared, so callers must synchronize on this object around each use.
 */
public class Database implements AutoCloseable {
	private final Connection connection;

	public Database(Path file) throws IOException, SQLException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try {
			this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
		} catch (SQLException e) {
			if (e.getMessage() != null && e.getMessage().contains("No suitable driver")) {
				throw new SQLException("SQLite driver not found. Start the app with run.ps1 or run.sh, which put "
						+ "lib/sqlite-jdbc-*.jar on the classpath.", e);
			}
			throw e;
		}
		try (Statement s = connection.createStatement()) {
			s.execute("PRAGMA foreign_keys = ON");
			s.execute("PRAGMA journal_mode = WAL");
			s.execute("CREATE TABLE IF NOT EXISTS users ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ "username TEXT NOT NULL UNIQUE COLLATE NOCASE,"
					+ "password_hash TEXT NOT NULL,"
					+ "created_at TEXT NOT NULL)");
			s.execute("CREATE TABLE IF NOT EXISTS sessions ("
					+ "token_hash TEXT PRIMARY KEY,"
					+ "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
					+ "expires_at INTEGER NOT NULL)");
			s.execute("CREATE TABLE IF NOT EXISTS calculations ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
					+ "name TEXT NOT NULL,"
					+ "created_at TEXT NOT NULL,"
					+ "inputs TEXT NOT NULL,"
					+ "results_json TEXT NOT NULL,"
					+ "total REAL NOT NULL)");
			s.execute("CREATE INDEX IF NOT EXISTS calculations_by_user ON calculations(user_id, id)");
		}
	}

	Connection connection() {
		return this.connection;
	}

	@Override
	public synchronized void close() throws SQLException {
		connection.close();
	}
}
