package com.gmail.bobason01;

import com.gmail.bobason01.blacklist.BlacklistManager;
import com.gmail.bobason01.command.DamageDisplayCommand;
import com.gmail.bobason01.data.IDataSource;
import com.gmail.bobason01.data.MySQLDataSource;
import com.gmail.bobason01.data.SQLiteDataSource;
import com.gmail.bobason01.data.YamlDataSource;
import com.gmail.bobason01.listener.EntityDamageListener;
import com.gmail.bobason01.listener.PlayerConnectionListener;
import com.gmail.bobason01.util.ResourcePackBuilder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

public final class DamageDisplay extends JavaPlugin {

    private IDataSource dataSource;
    private BlacklistManager blacklistManager;
    private DamageDisplayRendererImpl renderer;
    private ResourcePackBuilder resourcePackBuilder;

    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<EntityType, Vector> mobOffsets = new ConcurrentHashMap<>();
    private int maxSkinIndex = 0;

    private ExecutorService ioExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupIoExecutor();
        loadMobOffsets();
        initDataSource();
        updateMaxSkinIndex();

        resourcePackBuilder = new ResourcePackBuilder(getDataFolder(), ioExecutor);

        blacklistManager = new BlacklistManager(this, new File(getDataFolder(), "blacklist.yml"));
        renderer = new DamageDisplayRendererImpl(this);

        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        new DamageDisplayCommand(this);

        getLogger().info("DamageDisplay enabled");
    }

    @Override
    public void onDisable() {
        try {
            if (renderer != null) {
                renderer.removeAll();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Renderer cleanup error", t);
        }

        if (blacklistManager != null) {
            try {
                blacklistManager.saveSync();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to save blacklist on shutdown", e);
            }
        }

        if (dataSource != null) {
            try {
                dataSource.close().get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Data source close timeout or error", e);
            }
        }

        if (resourcePackBuilder != null) {
            try {
                resourcePackBuilder.shutdown();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "ResourcePackBuilder shutdown error", e);
            }
        }

        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        getLogger().info("DamageDisplay disabled");
    }

    private void setupIoExecutor() {
        this.ioExecutor = new ThreadPoolExecutor(
                2,
                4,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("DamageDisplay-IO");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    private void initDataSource() {
        FileConfiguration cfg = getConfig();
        String type = cfg.getString("storage.type", "YAML");
        String normalized = type == null ? "YAML" : type.trim().toUpperCase();

        switch (normalized) {
            case "MYSQL" -> dataSource = new MySQLDataSource(this, ioExecutor);
            case "SQLITE" -> dataSource = new SQLiteDataSource(this, ioExecutor);
            case "YAML" -> dataSource = new YamlDataSource(this, ioExecutor);
            default -> {
                getLogger().warning("Unknown storage type " + normalized + " defaulting to YAML");
                dataSource = new YamlDataSource(this, ioExecutor);
                normalized = "YAML";
            }
        }

        getLogger().info("Using " + normalized + " storage backend");

        try {
            boolean ok = dataSource.connect().get(10, TimeUnit.SECONDS);
            if (!ok) {
                getLogger().severe("Failed to connect storage backend");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Storage connect failed", e);
        }
    }

    private void loadMobOffsets() {
        mobOffsets.clear();
        File file = new File(getDataFolder(), "mob-offsets.yml");
        if (!file.exists()) {
            saveResource("mob-offsets.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                double x = cfg.getDouble(key + ".x", 0.0);
                double y = cfg.getDouble(key + ".y", 1.5);
                double z = cfg.getDouble(key + ".z", 0.0);
                mobOffsets.put(type, new Vector(x, y, z));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid entity type in mob-offsets.yml " + key);
            }
        }

        getLogger().info("Loaded " + mobOffsets.size() + " mob offsets");
    }

    private void updateMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        if (!dir.exists() && !dir.mkdirs()) {
            maxSkinIndex = 0;
            return;
        }

        int max = 0;
        File[] files = dir.listFiles((f, name) -> name.startsWith("normal") && name.endsWith(".png"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                int value = 0;
                boolean hasDigit = false;
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c >= '0' && c <= '9') {
                        hasDigit = true;
                        value = value * 10 + (c - '0');
                    }
                }
                if (hasDigit && value > max) {
                    max = value;
                }
            }
        }
        maxSkinIndex = max;
        getLogger().info("Max damage skin index " + maxSkinIndex);
    }

    public void reloadPlugin() {
        reloadConfig();
        loadMobOffsets();
        updateMaxSkinIndex();

        if (renderer != null) {
            try {
                renderer.removeAll();
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Renderer cleanup on reload error", t);
            }
        }
        renderer = new DamageDisplayRendererImpl(this);

        if (blacklistManager != null) {
            try {
                blacklistManager.saveSync();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Blacklist save on reload error", e);
            }
        }
        blacklistManager = new BlacklistManager(this, new File(getDataFolder(), "blacklist.yml"));

        getLogger().info("DamageDisplay reloaded");
    }

    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    public IDataSource getDataSource() {
        return dataSource;
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager != null && blacklistManager.isBlacklisted(type);
    }

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        if (dataSource != null) {
            dataSource.savePlayerSkin(uuid, skinIndex);
        }
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    public void loadPlayerSkinData(UUID uuid) {
        if (dataSource == null) {
            playerSkins.put(uuid, 0);
            return;
        }
        dataSource.loadPlayerSkin(uuid).thenAccept(skin -> {
            Bukkit.getScheduler().runTask(this, () -> playerSkins.put(uuid, skin));
        });
    }

    public void unloadPlayerSkinData(UUID uuid) {
        playerSkins.remove(uuid);
    }

    public Vector getMobOffset(org.bukkit.entity.Entity entity) {
        EntityType type = entity.getType();
        Vector custom = mobOffsets.get(type);
        if (custom != null) {
            return custom;
        }
        double h = entity.getHeight();
        return new Vector(0.0, h * 0.8 + 0.3, 0.0);
    }

    public int getMaxSkinIndex() {
        return maxSkinIndex;
    }

    public ResourcePackBuilder getResourcePackBuilder() {
        return resourcePackBuilder;
    }
}
