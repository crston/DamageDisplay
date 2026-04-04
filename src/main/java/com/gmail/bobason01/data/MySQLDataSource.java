package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MySQLDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private final Executor executor;
    private HikariDataSource ds;

    public MySQLDataSource(DamageDisplay plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FileConfiguration cfg = plugin.getConfig();
                String host = cfg.getString("storage.mysql.host", "localhost");
                int port = cfg.getInt("storage.mysql.port", 3306);
                String database = cfg.getString("storage.mysql.database", "minecraft");
                String user = cfg.getString("storage.mysql.username", "root");
                String pass = cfg.getString("storage.mysql.password", "");

                HikariConfig hConfig = new HikariConfig();
                hConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&useUnicode=true&characterEncoding=utf8");
                hConfig.setUsername(user);
                hConfig.setPassword(pass);

                // 안정성 설정
                hConfig.setConnectionTimeout(5000);
                hConfig.setKeepaliveTime(30000);
                hConfig.setMaxLifetime(1800000);
                hConfig.setMinimumIdle(2);
                hConfig.setMaximumPoolSize(10);
                hConfig.setPoolName("DamageDisplay-MySQL");

                this.ds = new HikariDataSource(hConfig);
                setupTable();
                plugin.getLogger().info("MySQL pool initialized successfully.");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not connect to MySQL", e);
                return false;
            }
        }, executor);
    }

    private void setupTable() {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_skins (uuid VARCHAR(36) PRIMARY KEY, skin_index INT DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to setup MySQL table", e);
        }
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT skin_index FROM player_skins WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("skin_index");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading skin for " + uuid, e);
            }
            return 0;
        }, executor).orTimeout(3, TimeUnit.SECONDS).exceptionally(ex -> 0);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO player_skins (uuid, skin_index) VALUES (?, ?) ON DUPLICATE KEY UPDATE skin_index = ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.setInt(3, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error saving skin for " + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> close() {
        if (ds != null && !ds.isClosed()) ds.close();
        return CompletableFuture.completedFuture(null);
    }
}