package com.gmail.bobason01;

import com.gmail.bobason01.blacklist.BlacklistManager;
import com.gmail.bobason01.command.BugReportCommand;
import com.gmail.bobason01.command.DamageDisplayCommand;
import com.gmail.bobason01.data.IDataSource;
import com.gmail.bobason01.data.MySQLDataSource;
import com.gmail.bobason01.data.SQLiteDataSource;
import com.gmail.bobason01.data.YamlDataSource;
import com.gmail.bobason01.listener.EntityDamageListener;
import com.gmail.bobason01.listener.PlayerConnectionListener;
import com.gmail.bobason01.util.ResourceFileCreator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageDisplay extends JavaPlugin {
    private IDataSource dataSource;
    private BlacklistManager blacklistManager;
    private DamageDisplayRendererImpl renderer;
    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();
    private int maxSkinIndex = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMobOffsets();
        initDataSource();
        updateMaxSkinIndex();

        new ResourceFileCreator(getDataFolder()).createResourceFiles();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        new DamageDisplayCommand(this);
        new BugReportCommand(this);

        getLogger().info("DamageDisplay enabled with Vanilla Critical system.");
    }

    @Override
    public void onDisable() {
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();
        if (dataSource != null) dataSource.close();
    }

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        dataSource.savePlayerSkin(uuid, skinIndex);
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    public void loadPlayerSkinData(UUID uuid) {
        dataSource.loadPlayerSkin(uuid).thenAcceptAsync(skinIndex -> playerSkins.put(uuid, skinIndex), runnable -> Bukkit.getScheduler().runTask(this, runnable));
    }

    public void unloadPlayerSkinData(UUID uuid) {
        playerSkins.remove(uuid);
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager.isBlacklisted(type);
    }

    private void initDataSource() {
        String storageType = getConfig().getString("storage.type", "YAML").toUpperCase();
        switch (storageType) {
            case "MYSQL":
                this.dataSource = new MySQLDataSource(this);
                break;
            case "SQLITE":
                this.dataSource = new SQLiteDataSource(this);
                break;
            case "YAML":
            default:
                if (!storageType.equals("YAML")) {
                    getLogger().warning("Invalid storage type '" + storageType + "'. Defaulting to YAML.");
                }
                this.dataSource = new YamlDataSource(this);
                break;
        }

        getLogger().info("Using " + storageType + " for data storage.");
        this.dataSource.connect();
    }

    private void loadMobOffsets() {
        mobOffsets.clear();
        File offsetsFile = new File(getDataFolder(), "mob-offsets.yml");
        if (!offsetsFile.exists()) {
            saveResource("mob-offsets.yml", false);
        }
        FileConfiguration offsetsConfig = YamlConfiguration.loadConfiguration(offsetsFile);

        if (offsetsConfig.getKeys(false).isEmpty()) {
            getLogger().info("mob-offsets.yml is empty. No custom mob offsets loaded.");
            return;
        }

        for (String key : offsetsConfig.getKeys(false)) {
            double x = offsetsConfig.getDouble(key + ".x", 0);
            double y = offsetsConfig.getDouble(key + ".y", 2.0);
            double z = offsetsConfig.getDouble(key + ".z", 0);
            mobOffsets.put(key, new Vector(x, y, z));
        }
        getLogger().info("Loaded " + mobOffsets.size() + " custom mob offsets.");
    }

    public Map<String, Vector> getMobOffsets() {
        return mobOffsets;
    }

    public int getMaxSkinIndex() {
        return this.maxSkinIndex;
    }

    private void updateMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        if (!dir.exists() && !dir.mkdirs()) {
            this.maxSkinIndex = 0;
            return;
        }
        int max = 0;
        File[] files = dir.listFiles((f, name) -> name.startsWith("normal") && name.endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                try {
                    int index = Integer.parseInt(file.getName().replaceAll("\\D+", ""));
                    if (index > max) max = index;
                } catch (NumberFormatException ignored) {}
            }
        }
        this.maxSkinIndex = max;
        getLogger().info("Max damage skin index cached: " + max);
    }

    public void reloadPlugin() {
        reloadConfig();
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();

        loadMobOffsets();
        updateMaxSkinIndex();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info("DamageDisplay fully reloaded.");
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }
}