package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public final class SQLiteDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private final Executor executor;
    private HikariDataSource dataSource;

    public SQLiteDataSource(DamageDisplay plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
                if (!dbFile.exists() && !dbFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create sqlite db file");
                }

                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                cfg.setPoolName("DamageDisplay-SQLite");
                cfg.setMaximumPoolSize(4);
                cfg.setMinimumIdle(1);
                cfg.setConnectionTimeout(8000);
                cfg.setMaxLifetime(1800000);
                cfg.setIdleTimeout(300000);

                dataSource = new HikariDataSource(cfg);

                createTable();
                plugin.getLogger().info("SQLite pool initialized");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite init error", e);
                return false;
            }
        }, executor);
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_skins (uuid TEXT PRIMARY KEY, skin_index INTEGER NOT NULL)";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite create table error", e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    plugin.getLogger().info("SQLite pool closed");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "SQLite close error", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT skin_index FROM player_skins WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite load failed for " + uuid, e);
            }
            return 0;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_skins (uuid, skin_index) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET skin_index = excluded.skin_index";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite save failed for " + uuid, e);
            }
        }, executor);
    }
}
