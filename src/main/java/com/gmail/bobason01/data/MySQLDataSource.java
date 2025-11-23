package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

public final class MySQLDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private final Executor executor;
    private HikariDataSource dataSource;

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

                HikariConfig hc = new HikariConfig();
                hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useUnicode=true&characterEncoding=utf8&useSSL=false");
                hc.setUsername(user);
                hc.setPassword(pass);
                hc.setPoolName("DamageDisplay-MySQL");
                hc.setMaximumPoolSize(10);
                hc.setMinimumIdle(2);
                hc.setConnectionTimeout(8000);
                hc.setIdleTimeout(300000);
                hc.setMaxLifetime(1800000);

                dataSource = new HikariDataSource(hc);
                createTable();
                plugin.getLogger().info("MySQL pool initialized");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "MySQL init error", e);
                return false;
            }
        }, executor);
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_skins (" +
                "uuid CHAR(36) NOT NULL PRIMARY KEY, " +
                "skin_index INT NOT NULL DEFAULT 0" +
                ")";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL create table error", e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    plugin.getLogger().info("MySQL pool closed");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "MySQL close error", e);
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
                plugin.getLogger().log(Level.SEVERE, "MySQL load failed for " + uuid, e);
            }
            return 0;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_skins (uuid, skin_index) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE skin_index = VALUES(skin_index)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "MySQL save failed for " + uuid, e);
            }
        }, executor);
    }
}
