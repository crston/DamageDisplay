package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * MySQLDataSource - Java 21 기반 고성능 비동기 데이터소스
 * - HikariCP 풀 기반 커넥션 관리
 * - Virtual Thread Executor 사용 (완전 비블로킹 I/O)
 * - 안전한 종료 및 로깅 최적화
 */
public final class MySQLDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private volatile HikariDataSource dataSource;

    // Virtual Thread Executor (Java 21 이상)
    private final ExecutorService ioExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("DamageDisplay-MySQL-", 0).factory()
    );

    public MySQLDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            FileConfiguration cfg = plugin.getConfig();
            try {
                HikariConfig hikari = new HikariConfig();
                hikari.setJdbcUrl("jdbc:mariadb://" +
                        cfg.getString("storage.mysql.host", "localhost") + ":" +
                        cfg.getInt("storage.mysql.port", 3306) + "/" +
                        cfg.getString("storage.mysql.database", "minecraft"));
                hikari.setUsername(cfg.getString("storage.mysql.username"));
                hikari.setPassword(cfg.getString("storage.mysql.password"));
                hikari.setMaximumPoolSize(10);
                hikari.setMinimumIdle(2);
                hikari.setConnectionTimeout(5000);
                hikari.setIdleTimeout(300_000);
                hikari.setMaxLifetime(1_800_000);
                hikari.setPoolName("DamageDisplayPool");

                this.dataSource = new HikariDataSource(hikari);
                createTable();
                plugin.getLogger().info(() -> "[MySQL] Connection pool initialized.");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[MySQL] Connection initialization failed.", e);
                return false;
            }
        }, ioExecutor);
    }

    private void createTable() {
        final String sql = """
                CREATE TABLE IF NOT EXISTS player_skins (
                    uuid CHAR(36) NOT NULL PRIMARY KEY,
                    skin_index INT NOT NULL DEFAULT 0
                );
                """;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Failed to create table player_skins.", e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    plugin.getLogger().info(() -> "[MySQL] Connection pool closed.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[MySQL] Error during shutdown.", e);
            } finally {
                ioExecutor.shutdownNow();
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        // Virtual Thread 기반 I/O → 커넥션 획득 대기 중에도 스레드 점유 없음
        return CompletableFuture.supplyAsync(() -> {
            final String sql = "SELECT skin_index FROM player_skins WHERE uuid = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[MySQL] Load failed for " + uuid, e);
            }
            return 0;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        return CompletableFuture.runAsync(() -> {
            final String sql = """
                    INSERT INTO player_skins (uuid, skin_index)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE skin_index = VALUES(skin_index);
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[MySQL] Save failed for " + uuid, e);
            }
        }, ioExecutor);
    }
}
