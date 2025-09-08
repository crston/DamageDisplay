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
    // [개선] 타입을 Impl로 명확히 하여 형변환 문제 방지
    private DamageDisplayRendererImpl renderer;
    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();

    // [핵심] maxSkinIndex 값을 캐싱할 변수
    private int maxSkinIndex = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMobOffsets();
        initDataSource();
        updateMaxSkinIndex(); // [핵심] 서버 시작 시 스킨 인덱스 계산

        new ResourceFileCreator(getDataFolder()).createResourceFiles();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        // [개선] 리스너 역할을 별도 클래스로 분리하여 등록
        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

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

    // PlayerConnectionListener 로 이동되었으므로 메인 클래스에서는 제거합니다.

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        // 데이터 소스 저장은 비동기로 처리하는 것이 좋습니다. (이미 그렇게 구현되어 있다면 OK)
        dataSource.savePlayerSkin(uuid, skinIndex);
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    // PlayerJoinEvent 처리를 위한 메서드
    public void loadPlayerSkinData(UUID uuid) {
        dataSource.loadPlayerSkin(uuid).thenAcceptAsync(skinIndex -> playerSkins.put(uuid, skinIndex), runnable -> Bukkit.getScheduler().runTask(this, runnable));
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager.isBlacklisted(type);
    }

    private void initDataSource() {
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

    // [핵심] 캐싱된 값을 즉시 반환하도록 변경
    public int getMaxSkinIndex() {
        return this.maxSkinIndex;
    }

    // [핵심] 실제 인덱스를 계산하는 로직
    private void updateMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
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
        updateMaxSkinIndex(); // [핵심] 리로드 시에도 스킨 인덱스 다시 계산

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info("DamageDisplay fully reloaded.");
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }
}