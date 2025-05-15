package com.gmail.bobason01;

import com.gmail.bobason01.blacklist.BlacklistManager;
import com.gmail.bobason01.command.DamageDisplayCommand;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class DamageDisplay extends JavaPlugin implements Listener {
    private BlacklistManager blacklistManager;
    private DamageDisplayRenderer renderer;
    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new ResourceFileCreator(getDataFolder()).createResourceFiles();

        this.blacklistManager = new BlacklistManager(this, getDataFolder());
        this.renderer = new DamageDisplayRendererImpl(this);

        getServer().getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        getServer().getPluginManager().registerEvents(this, this);

        new DamageDisplayCommand(this); // 명령어 통합 처리

        getLogger().info("DamageDisplay plugin enabled with renderer: " + renderer.getClass().getSimpleName());
    }

    @Override
    public void onDisable() {
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            saveSkin(uuid, 0);
        } else {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            int skin = config.getInt("damage-skin", 0);
            playerSkins.put(uuid, skin);
        }
    }

    public void saveSkin(UUID uuid, int skinIndex) {
        File file = getPlayerFile(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                FileConfiguration config = new YamlConfiguration();
                config.set("damage-skin", skinIndex);
                config.save(file);
                playerSkins.put(uuid, skinIndex);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save skin for " + uuid, e);
            }
        });
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager.isBlacklisted(type);
    }

    public int getMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        int max = 0;
        File[] files = dir.listFiles((f, name) -> name.startsWith("normal") && name.endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                try {
                    int index = Integer.parseInt(file.getName().replaceAll("\\D+", ""));
                    max = Math.max(max, index);
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    private File getPlayerFile(UUID uuid) {
        return new File(getDataFolder(), "saves/" + uuid + ".yml");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveIfDirtyAsync();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info("DamageDisplay fully reloaded.");
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }
}
