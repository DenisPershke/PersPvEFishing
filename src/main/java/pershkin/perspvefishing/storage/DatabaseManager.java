package pershkin.perspvefishing.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import pershkin.perspvefishing.model.FishCatch;
import pershkin.perspvefishing.model.PlayerStats;
import pershkin.perspvefishing.model.TopEntry;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class DatabaseManager {

    private static final String CREATE_PLAYERS_TABLE =
            "CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "total_fish INTEGER DEFAULT 0," +
                    "biggest_fish REAL DEFAULT 0," +
                    "money_earned REAL DEFAULT 0" +
                    ");";

    private static final String CREATE_CATCHES_TABLE =
            "CREATE TABLE IF NOT EXISTS fish_catches (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT," +
                    "rarity TEXT," +
                    "weight REAL," +
                    "value REAL," +
                    "caught_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    private final Plugin plugin;
    private Connection connection;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Cannot create plugin data directory.");
            }

            File databaseFile = new File(plugin.getDataFolder(), "database.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("PRAGMA journal_mode=WAL;");
                statement.executeUpdate(CREATE_PLAYERS_TABLE);
                statement.executeUpdate(CREATE_CATCHES_TABLE);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database.", ex);
            connection = null;
        }
    }

    public synchronized void recordCatch(UUID ownerUuid, FishCatch fishCatch) {
        if (connection == null) {
            return;
        }

        try {
            ensurePlayerExists(ownerUuid);

            try (PreparedStatement insertCatch = connection.prepareStatement(
                    "INSERT INTO fish_catches (owner_uuid, rarity, weight, value) VALUES (?, ?, ?, ?)")) {
                insertCatch.setString(1, ownerUuid.toString());
                insertCatch.setString(2, fishCatch.getRarity().getId());
                insertCatch.setDouble(3, fishCatch.getWeight());
                insertCatch.setDouble(4, fishCatch.getValue());
                insertCatch.executeUpdate();
            }

            try (PreparedStatement updateStats = connection.prepareStatement(
                    "UPDATE players SET total_fish = total_fish + 1, " +
                            "biggest_fish = CASE WHEN biggest_fish < ? THEN ? ELSE biggest_fish END " +
                            "WHERE uuid = ?")) {
                updateStats.setDouble(1, fishCatch.getWeight());
                updateStats.setDouble(2, fishCatch.getWeight());
                updateStats.setString(3, ownerUuid.toString());
                updateStats.executeUpdate();
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save fish catch.", ex);
        }
    }

    public synchronized void recordSale(UUID ownerUuid, double totalValue) {
        if (connection == null) {
            return;
        }

        try {
            ensurePlayerExists(ownerUuid);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE players SET money_earned = money_earned + ? WHERE uuid = ?")) {
                update.setDouble(1, totalValue);
                update.setString(2, ownerUuid.toString());
                update.executeUpdate();
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save fish sale.", ex);
        }
    }

    public synchronized PlayerStats getStats(UUID playerUuid) {
        if (connection == null) {
            return new PlayerStats(0, 0.0D, 0.0D);
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT total_fish, biggest_fish, money_earned FROM players WHERE uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new PlayerStats(0, 0.0D, 0.0D);
                }

                int totalFish = resultSet.getInt("total_fish");
                double biggest = resultSet.getDouble("biggest_fish");
                double moneyEarned = resultSet.getDouble("money_earned");
                return new PlayerStats(totalFish, biggest, moneyEarned);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read player stats.", ex);
            return new PlayerStats(0, 0.0D, 0.0D);
        }
    }

    public synchronized List<TopEntry> getTopByMoney(int limit) {
        List<TopEntry> entries = new ArrayList<TopEntry>();
        if (connection == null) {
            return entries;
        }

        int sanitizedLimit = Math.max(1, limit);
        String query =
                "SELECT uuid, money_earned, total_fish, biggest_fish " +
                        "FROM players ORDER BY money_earned DESC, total_fish DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, sanitizedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(resultSet.getString("uuid"));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }

                    double money = resultSet.getDouble("money_earned");
                    int totalFish = resultSet.getInt("total_fish");
                    double biggest = resultSet.getDouble("biggest_fish");
                    entries.add(new TopEntry(uuid, money, totalFish, biggest));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read top players.", ex);
        }

        return entries;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close SQLite connection.", ex);
        } finally {
            connection = null;
        }
    }

    private void ensurePlayerExists(UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO players (uuid) VALUES (?) ON CONFLICT(uuid) DO NOTHING")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }
}
