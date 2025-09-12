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
    // 타입을 Impl로 명확히 하여 형변환 문제 방지
    private DamageDisplayRendererImpl renderer;
    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();

    // maxSkinIndex 값을 캐싱할 변수
    private int maxSkinIndex = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMobOffsets();
        initDataSource();
        updateMaxSkinIndex(); // 서버 시작 시 스킨 인덱스 계산

        new ResourceFileCreator(getDataFolder()).createResourceFiles();

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        // 리스너 역할을 별도 클래스로 분리하여 등록
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

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        // 데이터 소스 저장은 비동기로 처리
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
        // [수정된 부분] 기본값을 "SQLITE"에서 "YAML"로 변경
        String storageType = getConfig().getString("storage.type", "YAML").toUpperCase();
        switch (storageType) {
            case "MYSQL" -> this.dataSource = new MySQLDataSource(this);
            // YAML을 기본값으로 사용하므로, default 케이스와 합쳐도 무방
            case "YAML" -> this.dataSource = new YamlDataSource(this);
            default -> {
                // 만약 YAML 이외의 잘못된 값이 들어올 경우를 대비해 SQLite를 fallback으로 두거나, YAML로 강제할 수 있음
                // 여기서는 YAML을 기본으로 하므로 YamlDataSource를 사용
                if (!storageType.equals("YAML")) {
                    getLogger().warning("Invalid storage type '" + storageType + "'. Defaulting to YAML.");
                }
                this.dataSource = new YamlDataSource(this);
            }
        }
        // SQLite는 이제 명시적으로 설정해야만 사용됩니다.
        if (storageType.equals("SQLITE")) {
            this.dataSource = new SQLiteDataSource(this);
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

    // 캐싱된 값을 즉시 반환하도록 변경
    public int getMaxSkinIndex() {
        return this.maxSkinIndex;
    }

    // 실제 인덱스를 계산하는 로직
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
        updateMaxSkinIndex(); // 리로드 시에도 스킨 인덱스 다시 계산

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info("DamageDisplay fully reloaded.");
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }
}