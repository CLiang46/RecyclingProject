package com.recycleproj;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved calculations. Every query is scoped to the owning user, so one user can never read or delete another's.
 */
public class CalculationStore {
	public static final int MAX_NAME_LENGTH = 100;
	public static final int MAX_PER_USER = 1000;

	public static final class Saved {
		public final long id;
		public final String name;
		public final String createdAt;
		public final double total;
		/** Form-encoded calculator inputs; null in list results. */
		public final String inputs;
		/** Result JSON from {@link WebServer#toJson}; null in list results. */
		public final String resultsJson;

		Saved(long id, String name, String createdAt, double total, String inputs, String resultsJson) {
			this.id = id;
			this.name = name;
			this.createdAt = createdAt;
			this.total = total;
			this.inputs = inputs;
			this.resultsJson = resultsJson;
		}
	}

	private final Database db;

	public CalculationStore(Database db) {
		this.db = db;
	}

	public Saved save(long userId, String name, String inputs, String resultsJson, double total) throws SQLException {
		String createdAt = Instant.now().toString();
		synchronized (db) {
			try (PreparedStatement count = db.connection().prepareStatement(
					"SELECT COUNT(*) FROM calculations WHERE user_id = ?")) {
				count.setLong(1, userId);
				try (ResultSet rs = count.executeQuery()) {
					rs.next();
					if (rs.getInt(1) >= MAX_PER_USER) {
						throw new IllegalArgumentException("You can save up to " + MAX_PER_USER
								+ " calculations. Delete some to save more.");
					}
				}
			}
			try (PreparedStatement ps = db.connection().prepareStatement(
					"INSERT INTO calculations (user_id, name, created_at, inputs, results_json, total) "
							+ "VALUES (?, ?, ?, ?, ?, ?)",
					PreparedStatement.RETURN_GENERATED_KEYS)) {
				ps.setLong(1, userId);
				ps.setString(2, name);
				ps.setString(3, createdAt);
				ps.setString(4, inputs);
				ps.setString(5, resultsJson);
				ps.setDouble(6, total);
				ps.executeUpdate();
				try (ResultSet keys = ps.getGeneratedKeys()) {
					keys.next();
					return new Saved(keys.getLong(1), name, createdAt, total, inputs, resultsJson);
				}
			}
		}
	}

	/** Newest first, without the inputs and results. */
	public List<Saved> list(long userId) throws SQLException {
		List<Saved> out = new ArrayList<>();
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"SELECT id, name, created_at, total FROM calculations WHERE user_id = ? ORDER BY id DESC")) {
				ps.setLong(1, userId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						out.add(new Saved(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), null, null));
					}
				}
			}
		}
		return out;
	}

	/** Returns the calculation, or null if it doesn't exist or belongs to someone else. */
	public Saved get(long userId, long id) throws SQLException {
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"SELECT id, name, created_at, total, inputs, results_json FROM calculations "
							+ "WHERE id = ? AND user_id = ?")) {
				ps.setLong(1, id);
				ps.setLong(2, userId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						return null;
					}
					return new Saved(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getString(5),
							rs.getString(6));
				}
			}
		}
	}

	/** Returns true if a calculation was deleted. */
	public boolean delete(long userId, long id) throws SQLException {
		synchronized (db) {
			try (PreparedStatement ps = db.connection().prepareStatement(
					"DELETE FROM calculations WHERE id = ? AND user_id = ?")) {
				ps.setLong(1, id);
				ps.setLong(2, userId);
				return ps.executeUpdate() > 0;
			}
		}
	}
}
