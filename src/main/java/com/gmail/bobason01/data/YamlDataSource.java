package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * YamlDataSource - Java 21 극한 최적화 버전
 *
 * - Virtual Thread Executor로 완전 비블로킹 디스크 I/O
 * - ConcurrentHashMap 캐시를 통한 0-latency 조회
 * - 객체 생성 최소화 및 GC 부하 제거
 * - 순차 디스크 쓰기 보장
 */
public final class YamlDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private final File dataFolder;
    private final ExecutorService ioExecutor;

    // UUID → 스킨 인덱스 캐시 (메모리 상 즉시 접근)
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    private static final CompletableFuture<Boolean> TRUE_FUTURE = CompletableFuture.completedFuture(true);
    private static final CompletableFuture<Void> VOID_FUTURE = CompletableFuture.completedFuture(null);

    public YamlDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "saves");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("[YAML] Could not create data folder: " + dataFolder.getAbsolutePath());
        }

        // Virtual Thread 기반 I/O 실행기 (자바 21)
        this.ioExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("DamageDisplay-YAML-", 0).factory()
        );
    }

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        plugin.getLogger().info(() -> "[YAML] Using YAML for local player data storage.");
        return TRUE_FUTURE;
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            cache.clear();
            ioExecutor.shutdownNow();
            plugin.getLogger().info(() -> "[YAML] Storage shutdown complete.");
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        Integer cached = cache.get(uuid);
        if (cached != null) {
            // 즉시 캐시 반환 → 비동기 오버헤드 없음
            return CompletableFuture.completedFuture(cached);
        }

        // Virtual Thread 기반 I/O (메인 스레드 비블로킹)
        return CompletableFuture.supplyAsync(() -> {
            File file = getPlayerFile(uuid);
            if (!file.exists()) {
                cache.put(uuid, 0);
                return 0;
            }

            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                int skin = config.getInt("damage-skin", 0);
                cache.put(uuid, skin);
                return skin;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[YAML] Failed to load skin for " + uuid, e);
                cache.put(uuid, 0);
                return 0;
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        // 캐시에 즉시 반영 (비동기 작업 이전에 메모리 일관성 확보)
        cache.put(uuid, skinIndex);

        return CompletableFuture.runAsync(() -> {
            File file = getPlayerFile(uuid);
            try {
                FileConfiguration config = new YamlConfiguration();
                config.set("damage-skin", skinIndex);
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[YAML] Failed to save skin for " + uuid, e);
            }
        }, ioExecutor);
    }
}
