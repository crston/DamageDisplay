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
import com.gmail.bobason01.util.ResourcePackBuilder;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
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
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();
    private int maxSkinIndex = 0;

    private ExecutorService ioExecutor;

    private int animationMode;
    private int animationDuration;
    private float scaleBase;
    private float scalePerDamage;
    private float scaleMax;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        reloadConfig();

        setupIoExecutor();
        loadMobOffsets();
        loadAnimationSettings();
        initDataSource();

        resourcePackBuilder = new ResourcePackBuilder(this, ioExecutor);
        updateMaxSkinIndex();
        resourcePackBuilder.buildAsync();

        blacklistManager = new BlacklistManager(this, new File(getDataFolder(), "blacklist.yml"));
        renderer = new DamageDisplayRendererImpl(this);

        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        if (getCommand("damagedisplay") != null) {
            new DamageDisplayCommand(this);
        }

        if (getCommand("crston") != null) {
            new BugReportCommand(this);
        }

        getLogger().info("DamageDisplay enabled");
    }

    @Override
    public void onDisable() {
        if (renderer != null) renderer.removeAll();
        if (blacklistManager != null) blacklistManager.saveSync();
        if (dataSource != null) {
            try {
                dataSource.close().get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "DataSource close warning", e);
            }
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
            }
        }
    }

    private void updateConfig() {
        FileConfiguration cfg = getConfig();
        boolean changed = false;
        if (!cfg.contains("animation.mode")) { cfg.set("animation.mode", 1); changed = true; }
        if (!cfg.contains("animation.duration")) { cfg.set("animation.duration", 15); changed = true; }
        if (!cfg.contains("animation.scaling.base")) { cfg.set("animation.scaling.base", 0.25); changed = true; }
        if (!cfg.contains("animation.scaling.per-damage")) { cfg.set("animation.scaling.per-damage", 0.05); changed = true; }
        if (!cfg.contains("animation.scaling.max")) { cfg.set("animation.scaling.max", 1.25); changed = true; }
        if (!cfg.contains("storage.type")) { cfg.set("storage.type", "YAML"); changed = true; }
        if (changed) saveConfig();
    }

    private void setupIoExecutor() {
        this.ioExecutor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "DamageDisplay-IO");
            t.setDaemon(true);
            return t;
        });
    }

    private void initDataSource() {
        String type = getConfig().getString("storage.type", "YAML").toUpperCase();
        dataSource = switch (type) {
            case "MYSQL" -> new MySQLDataSource(this, ioExecutor);
            case "SQLITE" -> new SQLiteDataSource(this, ioExecutor);
            default -> new YamlDataSource(this, ioExecutor);
        };
        dataSource.connect().thenAccept(success -> {
            if (!success) getLogger().severe("Failed to connect to storage");
        });
    }

    private void loadAnimationSettings() {
        FileConfiguration cfg = getConfig();
        animationMode = cfg.getInt("animation.mode", 1);
        animationDuration = cfg.getInt("animation.duration", 15);
        scaleBase = (float) cfg.getDouble("animation.scaling.base", 0.25);
        scalePerDamage = (float) cfg.getDouble("animation.scaling.per-damage", 0.05);
        scaleMax = (float) cfg.getDouble("animation.scaling.max", 1.25);
    }

    public void loadMobOffsets() {
        mobOffsets.clear();
        File file = new File(getDataFolder(), "mob-offsets.yml");
        if (!file.exists()) saveResource("mob-offsets.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            mobOffsets.put(key.toLowerCase(), new Vector(
                    cfg.getDouble(key + ".x", 0.0),
                    cfg.getDouble(key + ".y", 1.5),
                    cfg.getDouble(key + ".z", 0.0)
            ));
        }
    }

    public void updateMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        if (!dir.exists()) { dir.mkdirs(); maxSkinIndex = 0; return; }
        int max = 0;
        File[] files = dir.listFiles((f, name) -> name.endsWith(".png"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                int val = extractNumber(name);
                if (val > max) max = val;
            }
        }
        this.maxSkinIndex = max;
    }

    private int extractNumber(String text) {
        int val = 0;
        boolean found = false;
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') {
                val = val * 10 + (c - '0');
                found = true;
            }
        }
        return found ? val : -1;
    }

    public void reloadPlugin() {
        reloadConfig();
        updateConfig();
        loadAnimationSettings();
        loadMobOffsets();
        updateMaxSkinIndex();
        if (renderer != null) renderer.removeAll();
        renderer = new DamageDisplayRendererImpl(this);
        blacklistManager.load();
        resourcePackBuilder.buildAsync();
    }

    public Vector getMobOffset(Entity entity) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            var mmInst = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
            if (mmInst != null) {
                Vector v = mobOffsets.get(mmInst.getType().getInternalName().toLowerCase());
                if (v != null) return v;
            }
        }
        Vector v = mobOffsets.get(entity.getType().name().toLowerCase());
        return v != null ? v : new Vector(0.0, entity.getHeight() * 0.85, 0.0);
    }

    public ExecutorService getIoExecutor() { return ioExecutor; }
    public int getPlayerSkin(UUID uuid) { return playerSkins.getOrDefault(uuid, 0); }
    public void saveSkin(UUID uuid, int index) { playerSkins.put(uuid, index); if (dataSource != null) dataSource.savePlayerSkin(uuid, index); }
    public void loadPlayerSkinData(UUID uuid) { if (dataSource != null) dataSource.loadPlayerSkin(uuid).thenAccept(s -> playerSkins.put(uuid, s)); else playerSkins.put(uuid, 0); }
    public void unloadPlayerSkinData(UUID uuid) { playerSkins.remove(uuid); }
    public BlacklistManager getBlacklistManager() { return blacklistManager; }
    public ResourcePackBuilder getResourcePackBuilder() { return resourcePackBuilder; }
    public int getMaxSkinIndex() { return maxSkinIndex; }
    public int getAnimationMode() { return animationMode; }
    public int getAnimationDuration() { return animationDuration; }
    public float getScaleBase() { return scaleBase; }
    public float getScalePerDamage() { return scalePerDamage; }
    public float getScaleMax() { return scaleMax; }
    public boolean isEntityBlacklisted(EntityType type) { return blacklistManager.isBlacklisted(type); }
}