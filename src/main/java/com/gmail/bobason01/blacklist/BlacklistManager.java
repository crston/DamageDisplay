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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BlacklistManager {

    private final DamageDisplay plugin;
    private final File file;
    private final Set<EntityType> blacklisted = ConcurrentHashMap.newKeySet();

    public BlacklistManager(DamageDisplay plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    private void load() {
        if (!file.exists()) {
            saveSync();
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                if (cfg.getBoolean(key)) {
                    blacklisted.add(type);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in blacklist " + key);
            }
        }

        plugin.getLogger().info("Loaded blacklist size " + blacklisted.size());
    }

    public void saveSync() {
        FileConfiguration cfg = new YamlConfiguration();
        for (EntityType type : blacklisted) {
            cfg.set(type.name(), true);
        }

        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            cfg.save(temp);
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save blacklist.yml", e);
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    public Set<EntityType> getBlacklisted() {
        return Collections.unmodifiableSet(blacklisted);
    }

    public boolean isBlacklisted(EntityType type) {
        return blacklisted.contains(type);
    }

    public boolean addToBlacklist(EntityType type) {
        boolean added = blacklisted.add(type);
        if (added) {
            saveSync();
        }
        return added;
    }

    public boolean removeFromBlacklist(EntityType type) {
        boolean removed = blacklisted.remove(type);
        if (removed) {
            saveSync();
        }
        return removed;
    }
}
