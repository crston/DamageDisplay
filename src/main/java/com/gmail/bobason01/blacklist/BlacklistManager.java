package com.gmail.bobason01.blacklist;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlacklistManager {
    private static final Logger LOGGER = Logger.getLogger(BlacklistManager.class.getName());

    private final Set<EntityType> blacklist = ConcurrentHashMap.newKeySet();
    private final File blacklistFile;
    private final FileConfiguration blacklistConfig;
    private final Plugin plugin;
    private volatile boolean dirty = false;

    public BlacklistManager(Plugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.blacklistFile = new File(dataFolder, "blacklist.yml");
        this.blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
        loadBlacklist();
        startAutoSaveTask();
    }

    public void addToBlacklist(EntityType type) {
        if (blacklist.add(type)) dirty = true;
    }

    public void removeFromBlacklist(EntityType type) {
        if (blacklist.remove(type)) dirty = true;
    }

    public boolean isBlacklisted(EntityType type) {
        return blacklist.contains(type);
    }

    public Set<EntityType> getBlacklisted() {
        return blacklist;
    }

    private void loadBlacklist() {
        blacklist.clear();
        List<String> names = blacklistConfig.getStringList("blacklist");
        for (String name : names) {
            try {
                blacklist.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid EntityType in blacklist: " + name);
            }
        }
    }

    public void saveIfDirtyAsync() {
        if (!dirty) return;
        dirty = false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> entityNames = blacklist.stream().map(Enum::name).toList();
            blacklistConfig.set("blacklist", entityNames);
            try {
                blacklistConfig.save(blacklistFile);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to save blacklist", e);
            }
        });
    }

    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfDirtyAsync, 6000L, 6000L);
    }
}
