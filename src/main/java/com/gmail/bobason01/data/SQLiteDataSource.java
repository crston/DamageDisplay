package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteDataSource implements IDataSource {
    private final DamageDisplay plugin;
    private HikariDataSource dataSource;

    public SQLiteDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() {
        File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite database file.", e);
                return;
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setPoolName("DamageDisplay-SQLite-Pool");
        config.setMaximumPoolSize(10); // SQLite can handle a few more connections
        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("SQLite connection pool successfully initialized.");
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_skins (`uuid` TEXT NOT NULL PRIMARY KEY, `skin_index` INTEGER NOT NULL DEFAULT 0);";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create player_skins table in SQLite.", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT skin_index FROM player_skins WHERE uuid = ?;";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("skin_index");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load SQLite skin for " + uuid, e);
            }
            return 0;
        });
    }

    @Override
    public void savePlayerSkin(UUID uuid, int skinIndex) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_skins (uuid, skin_index) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET skin_index = ?;";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.setInt(3, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save SQLite skin for " + uuid, e);
            }
        });
    }
}