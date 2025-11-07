package com.gmail.bobason01.blacklist;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * BlacklistManager - DamageDisplay에서 데미지 표시를 제외할 엔티티를 관리.
 * Java 21 기반 극한의 성능 최적화 버전.
 * 비동기 저장, 동시 접근 안전성, I/O 최소화를 포함.
 */
public final class BlacklistManager {

    private final DamageDisplay plugin;
    private final File file;
    private final Set<EntityType> blacklisted;
    private final ExecutorService ioExecutor;
    private volatile boolean dirty = false;

    public BlacklistManager(DamageDisplay plugin, File dataFolder) {
        this.plugin = plugin;
        this.file = new File(dataFolder, "blacklist.yml");
        this.blacklisted = ConcurrentHashMap.newKeySet();
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Blacklist-IO");
            t.setDaemon(true);
            return t;
        });
        load();
    }

    /** 블랙리스트 로드 */
    private void load() {
        if (!file.exists()) {
            saveSync();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                if (config.getBoolean(key)) {
                    blacklisted.add(type);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in blacklist.yml: " + key);
            }
        }
        plugin.getLogger().info("Loaded blacklist (" + blacklisted.size() + " entries)");
    }

    /** 블랙리스트 저장 (동기) */
    private void saveSync() {
        FileConfiguration config = new YamlConfiguration();
        for (EntityType type : blacklisted) {
            config.set(type.name(), true);
        }
        File tempFile = new File(file.getParent(), file.getName() + ".tmp");
        try {
            config.save(tempFile);
            Files.move(tempFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save blacklist.yml", e);
        }
    }

    /** 변경 사항 비동기 저장 */
    public void saveIfDirtyAsync() {
        if (!dirty) return;
        dirty = false;
        CompletableFuture.runAsync(this::saveSync, ioExecutor)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Async blacklist save failed", ex);
                    return null;
                });
    }

    /** 블랙리스트 엔티티 목록 반환 */
    public Set<EntityType> getBlacklisted() {
        return Collections.unmodifiableSet(blacklisted);
    }

    /** 블랙리스트에 추가 */
    public void addToBlacklist(EntityType type) {
        if (blacklisted.add(type)) {
            dirty = true;
        }
    }

    /** 블랙리스트에서 제거 */
    public void removeFromBlacklist(EntityType type) {
        if (blacklisted.remove(type)) {
            dirty = true;
        }
    }

    /** 엔티티가 블랙리스트에 포함되어 있는지 확인 */
    public boolean isBlacklisted(EntityType type) {
        return blacklisted.contains(type);
    }

    /** 종료 시 Executor 정리 */
    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
