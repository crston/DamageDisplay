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
import java.util.logging.Level;

public class MySQLDataSource implements IDataSource {
    private final DamageDisplay plugin;
    private HikariDataSource dataSource;

    public MySQLDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() {
        FileConfiguration config = plugin.getConfig();
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.getString("storage.mysql.host") + ":" +
                    config.getInt("storage.mysql.port") + "/" + config.getString("storage.mysql.database"));
            hikariConfig.setUsername(config.getString("storage.mysql.username"));
            hikariConfig.setPassword(config.getString("storage.mysql.password"));
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("MySQL connection pool successfully initialized.");
            createTable();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create MySQL connection pool.", e);
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_skins (`uuid` VARCHAR(36) NOT NULL PRIMARY KEY, `skin_index` INT NOT NULL DEFAULT 0);";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create player_skins table in MySQL.", e);
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
                plugin.getLogger().log(Level.SEVERE, "Failed to load MySQL skin for " + uuid, e);
            }
            return 0;
        });
    }

    @Override
    public void savePlayerSkin(UUID uuid, int skinIndex) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_skins (uuid, skin_index) VALUES (?, ?) ON DUPLICATE KEY UPDATE skin_index = ?;";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.setInt(3, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save MySQL skin for " + uuid, e);
            }
        });
    }
}