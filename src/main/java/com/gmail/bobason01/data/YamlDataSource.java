package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class YamlDataSource implements IDataSource {

    private final DamageDisplay plugin;
    private final Executor executor;
    private final File dir;
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public YamlDataSource(DamageDisplay plugin, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
        this.dir = new File(plugin.getDataFolder(), "saves");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create saves directory " + dir.getAbsolutePath());
        }
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        plugin.getLogger().info("YamlDataSource is ready");
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> close() {
        cache.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        Integer cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            File file = new File(dir, uuid.toString() + ".yml");
            if (!file.exists()) {
                cache.put(uuid, 0);
                return 0;
            }

            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                int skin = cfg.getInt("damage-skin", 0);
                cache.put(uuid, skin);
                return skin;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load yaml for " + uuid, e);
                cache.put(uuid, 0);
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex) {
        cache.put(uuid, skinIndex);
        return CompletableFuture.runAsync(() -> {
            File file = new File(dir, uuid.toString() + ".yml");
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("damage-skin", skinIndex);
            try {
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save yaml for " + uuid, e);
            }
        }, executor);
    }
}
