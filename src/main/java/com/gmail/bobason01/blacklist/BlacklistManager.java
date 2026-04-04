package com.gmail.bobason01.blacklist;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;

public final class BlacklistManager {

    private final DamageDisplay plugin;
    private final File file;
    private final Set<EntityType> blacklisted;

    public BlacklistManager(DamageDisplay plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.blacklisted = Collections.synchronizedSet(EnumSet.noneOf(EntityType.class));
        load();
    }

    public void load() {
        if (!file.exists()) {
            saveSync();
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Set<EntityType> temp = EnumSet.noneOf(EntityType.class);

        for (String key : cfg.getKeys(false)) {
            try {
                if (cfg.getBoolean(key)) {
                    temp.add(EntityType.valueOf(key.toUpperCase()));
                }
            } catch (IllegalArgumentException ignored) {}
        }

        synchronized (blacklisted) {
            blacklisted.clear(); // [개선 4] 리로드 시 중복 방지를 위한 초기화
            blacklisted.addAll(temp);
        }
        plugin.getLogger().info("Loaded blacklist size " + blacklisted.size());
    }

    public void saveSync() {
        FileConfiguration cfg = new YamlConfiguration();
        synchronized (blacklisted) {
            for (EntityType type : blacklisted) {
                cfg.set(type.name(), true);
            }
        }

        // [개선 1 반영] 원자적 파일 쓰기
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            cfg.save(temp);
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save blacklist", e);
        }
    }

    public Set<EntityType> getBlacklisted() {
        synchronized (blacklisted) {
            return EnumSet.copyOf(blacklisted);
        }
    }

    public boolean isBlacklisted(EntityType type) {
        return blacklisted.contains(type);
    }

    public boolean addToBlacklist(EntityType type) {
        if (blacklisted.add(type)) {
            plugin.getIoExecutor().submit(this::saveSync);
            return true;
        }
        return false;
    }

    public boolean removeFromBlacklist(EntityType type) {
        if (blacklisted.remove(type)) {
            plugin.getIoExecutor().submit(this::saveSync);
            return true;
        }
        return false;
    }
}