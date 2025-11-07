package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * SQLiteDataSource - Java 21 극한 최적화 버전
 * - Virtual Thread Executor로 완전 비블로킹 I/O
 * - HikariCP 풀 튜닝
 * - PRAGMA 세팅 최적화
 * - GC 부하 0 구조
 */
public final class SQLiteDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private volatile HikariDataSource dataSource;

    // Virtual Thread Executor (자바 21)
    private final ExecutorService ioExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("DamageDisplay-SQLite-", 0).factory()
    );

    private static final CompletableFuture<Boolean> TRUE_FUTURE = CompletableFuture.completedFuture(true);
    private static final CompletableFuture<Void> VOID_FUTURE = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<Integer> ZERO_FUTURE = CompletableFuture.completedFuture(0);

    public SQLiteDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
                if (!dbFile.exists() && !dbFile.createNewFile()) {
                    plugin.getLogger().warning("[SQLite] Database file creation failed (exists or no permission).");
                }

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                config.setPoolName("DamageDisplay-SQLitePool");
                config.setMaximumPoolSize(1);      // 단일 커넥션 모드 유지
                config.setMinimumIdle(1);
                config.setConnectionTimeout(8000);
                config.setMaxLifetime(1_800_000);
                config.setIdleTimeout(300_000);
                config.addDataSourceProperty("journal_mode", "WAL");
                config.addDataSourceProperty("synchronous", "NORMAL");

                dataSource = new HikariDataSource(config);

                // PRAGMA 튜닝
                try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                    st.execute("PRAGMA journal_mode = WAL;");
                    st.execute("PRAGMA synchronous = NORMAL;");
                    st.execute("PRAGMA cache_size = 10000;");
                    st.execute("PRAGMA temp_store = MEMORY;");
                    st.execute("PRAGMA locking_mode = EXCLUSIVE;");
                }

                createTable();
                plugin.getLogger().info(() -> "[SQLite] Connection pool initialized and optimized.");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[SQLite] Initialization error.", e);
                return false;
            }
        }, ioExecutor);
    }

    private void createTable() {
        final String sql = """
                CREATE TABLE IF NOT EXISTS player_skins (
                    uuid TEXT NOT NULL PRIMARY KEY,
                    skin_index INTEGER NOT NULL DEFAULT 0
                );
                """;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to create player_skins table.", e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                    plugin.getLogger().info(() -> "[SQLite] Connection pool closed.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[SQLite] Error closing database.", e);
            } finally {
                ioExecutor.shutdownNow();
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            final String sql = "SELECT skin_index FROM player_skins WHERE uuid = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[SQLite] Load failed for " + uuid, e);
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
                    ON CONFLICT(uuid) DO UPDATE SET skin_index = excluded.skin_index;
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, skinIndex);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[SQLite] Save failed for " + uuid, e);
            }
        }, ioExecutor);
    }
}
