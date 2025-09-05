package com.gmail.bobason01;

import com.gmail.bobason01.blacklist.BlacklistManager;
import com.gmail.bobason01.command.BugReportCommand;
import com.gmail.bobason01.command.DamageDisplayCommand;
import com.gmail.bobason01.data.IDataSource;
import com.gmail.bobason01.data.MySQLDataSource;
import com.gmail.bobason01.data.SQLiteDataSource;
import com.gmail.bobason01.data.YamlDataSource;
import com.gmail.bobason01.listener.EntityDamageListener;
import com.gmail.bobason01.util.DamageDisplayRenderer;
import com.gmail.bobason01.util.ResourceFileCreator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DamageDisplay extends JavaPlugin implements Listener {
    private IDataSource dataSource;
    private BlacklistManager blacklistManager;
    private DamageDisplayRenderer renderer;
    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMobOffsets();
        initDataSource();

        new ResourceFileCreator(getDataFolder()).createResourceFiles();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        new DamageDisplayCommand(this);
        new BugReportCommand(this);

        getLogger().info("DamageDisplay enabled with renderer: " + renderer.getClass().getSimpleName());
    }

    @Override
    public void onDisable() {
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();
        if (dataSource != null) dataSource.close();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dataSource.loadPlayerSkin(uuid).thenAcceptAsync(skinIndex -> playerSkins.put(uuid, skinIndex), runnable -> Bukkit.getScheduler().runTask(this, runnable));
    }

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        dataSource.savePlayerSkin(uuid, skinIndex);
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager.isBlacklisted(type);
    }

    private void initDataSource() {
        // getConfig() 호출 시 문제가 발생하면 config.yml 파일의 문법 오류입니다.
        String storageType = getConfig().getString("storage.type", "SQLITE").toUpperCase();
        switch (storageType) {
            case "MYSQL" -> this.dataSource = new MySQLDataSource(this);
            case "YAML" -> this.dataSource = new YamlDataSource(this);
            default -> this.dataSource = new SQLiteDataSource(this);
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
        File dir = new File(getDataFolder(), "images");
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
        return max;
    }

    public void reloadPlugin() {
        reloadConfig();
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();

        loadMobOffsets();
        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info("DamageDisplay fully reloaded.");
    }



    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }
}