package com.gmail.bobason01.data;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class YamlDataSource implements IDataSource {
    private final DamageDisplay plugin;
    private final File dataFolder;

    public YamlDataSource(DamageDisplay plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "saves");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }

    @Override
    public void connect() {
        plugin.getLogger().info("Using YAML for data storage.");
    }

    @Override
    public void close() {}

    @Override
    public CompletableFuture<Integer> loadPlayerSkin(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            File file = getPlayerFile(uuid);
            if (!file.exists()) {
                return 0;
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            return config.getInt("damage-skin", 0);
        });
    }

    @Override
    public void savePlayerSkin(UUID uuid, int skinIndex) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File file = getPlayerFile(uuid);
                FileConfiguration config = new YamlConfiguration();
                config.set("damage-skin", skinIndex);
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save YAML skin for " + uuid, e);
            }
        });
    }
}
