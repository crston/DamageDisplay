package com.gmail.bobason01;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlacklistManager {
    private static final Logger LOGGER = Logger.getLogger(BlacklistManager.class.getName());
    private final Set<EntityType> blacklist;
    private final File blacklistFile;
    private final FileConfiguration blacklistConfig;

    public BlacklistManager(File dataFolder) {
        this.blacklist = new HashSet<>();
        this.blacklistFile = new File(dataFolder, "blacklist.yml");
        this.blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
        loadBlacklist();
    }

    public void addToBlacklist(EntityType entityType) {
        synchronized (blacklist) {
            blacklist.add(entityType);
        }
        saveBlacklist();
    }

    public void removeFromBlacklist(EntityType entityType) {
        synchronized (blacklist) {
            blacklist.remove(entityType);
        }
        saveBlacklist();
    }

    public boolean isBlacklisted(EntityType entityType) {
        synchronized (blacklist) {
            return blacklist.contains(entityType);
        }
    }

    private void saveBlacklist() {
        CompletableFuture.runAsync(() -> {
            synchronized (blacklist) {
                blacklistConfig.set("blacklist", new HashSet<>(blacklist));
                try {
                    blacklistConfig.save(blacklistFile);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error saving blacklist to file", e);
                }
            }
        });
    }

    private void loadBlacklist() {
        CompletableFuture.runAsync(() -> {
            synchronized (blacklist) {
                if (blacklistConfig.contains("blacklist")) {
                    for (String entityTypeName : blacklistConfig.getStringList("blacklist")) {
                        try {
                            blacklist.add(EntityType.valueOf(entityTypeName));
                        } catch (IllegalArgumentException e) {
                            LOGGER.log(Level.WARNING, "Invalid EntityType in blacklist: " + entityTypeName, e);
                        }
                    }
                } else {
                    saveBlacklist();
                }
            }
        });
    }
}