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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class DamageDisplay extends JavaPlugin {

    private IDataSource dataSource;
    private BlacklistManager blacklistManager;
    private DamageDisplayRendererImpl renderer;
    private ResourceFileCreator resourceBuilder;

    private final Map<UUID, Integer> playerSkins = new ConcurrentHashMap<>();
    private final Map<String, Vector> mobOffsets = new ConcurrentHashMap<>();
    private int maxSkinIndex = 0;

    // 플러그인 전용 I O 실행기 가상 스레드 기반
    private final ExecutorService ioExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("DamageDisplay-IO-", 0).factory());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMobOffsets();
        initDataSource();
        updateMaxSkinIndex();

        // 리소스팩 생성 비동기 실행
        resourceBuilder = new ResourceFileCreator(getDataFolder());
        CompletableFuture.runAsync(resourceBuilder::createResourceFiles, ioExecutor)
                .exceptionally(ex -> {
                    getLogger().log(Level.SEVERE, "[Resource] Build failed", ex);
                    return null;
                });

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        Bukkit.getPluginManager().registerEvents(new EntityDamageListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        new DamageDisplayCommand(this);
        new BugReportCommand(this);

        getLogger().info(() -> "DamageDisplay enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (renderer != null) {
            try {
                renderer.removeAll();
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Renderer cleanup error", t);
            }
        }

        // 블랙리스트 저장 보장 호출
        if (blacklistManager != null) {
            try {
                // saveIfDirtyAsync가 CompletableFuture 반환하는 구현을 가정
                // 기존 void 시그니처라면 경고 없이 넘어가도록 안전 처리
                var saveFutureRef = new AtomicReference<CompletableFuture<Void>>();
                try {
                    var m = BlacklistManager.class.getMethod("saveIfDirtyAsync");
                    var ret = m.invoke(blacklistManager);
                    if (ret instanceof CompletableFuture) {
                        //noinspection unchecked
                        saveFutureRef.set((CompletableFuture<Void>) ret);
                    }
                } catch (NoSuchMethodException ignored) {
                    // 메서드가 없거나 void 반환이면 무시
                } catch (Exception reflectError) {
                    getLogger().log(Level.WARNING, "Failed to invoke blacklist save", reflectError);
                }
                var f = saveFutureRef.get();
                if (f != null) {
                    f.get(2, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
            }
        }

        // 데이터 소스 종료 동기 대기
        if (dataSource != null) {
            try {
                dataSource.close().toCompletableFuture().get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "DataSource close timeout or error", e);
            }
        }

        // 리소스 빌더 종료
        ResourceFileCreator.shutdown();

        // 플러그인 전용 I O 실행기 종료
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        getLogger().info(() -> "DamageDisplay disabled cleanly.");
    }

    public void saveSkin(UUID uuid, int skinIndex) {
        playerSkins.put(uuid, skinIndex);
        dataSource.savePlayerSkin(uuid, skinIndex);
    }

    public int getPlayerSkin(UUID uuid) {
        return playerSkins.getOrDefault(uuid, 0);
    }

    public void loadPlayerSkinData(UUID uuid) {
        dataSource.loadPlayerSkin(uuid).thenAcceptAsync(
                skinIndex -> playerSkins.put(uuid, skinIndex),
                command -> Bukkit.getScheduler().runTask(this, command)
        );
    }

    public void unloadPlayerSkinData(UUID uuid) {
        playerSkins.remove(uuid);
    }

    public boolean isEntityBlacklisted(EntityType type) {
        return blacklistManager != null && blacklistManager.isBlacklisted(type);
    }

    private void initDataSource() {
        String storageType = getConfig().getString("storage.type", "YAML");
        String normalized = storageType == null ? "YAML" : storageType.toUpperCase();

        switch (normalized) {
            case "MYSQL":
                dataSource = new MySQLDataSource(this);
                break;
            case "SQLITE":
                dataSource = new SQLiteDataSource(this);
                break;
            case "YAML":
            default:
                if (!"YAML".equals(normalized)) {
                    getLogger().warning(() -> "Invalid storage type '" + normalized + "'. Defaulting to YAML.");
                }
                dataSource = new YamlDataSource(this);
                break;
        }

        getLogger().info(() -> "Using " + normalized + " for data storage.");
        dataSource.connect().exceptionally(ex -> {
            getLogger().log(Level.SEVERE, "[Storage] Connect failed", ex);
            return false;
        });
    }

    private void loadMobOffsets() {
        mobOffsets.clear();
        File offsetsFile = new File(getDataFolder(), "mob-offsets.yml");
        if (!offsetsFile.exists()) saveResource("mob-offsets.yml", false);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(offsetsFile);
        for (String key : cfg.getKeys(false)) {
            double x = cfg.getDouble(key + ".x", 0.0);
            double y = cfg.getDouble(key + ".y", 2.0);
            double z = cfg.getDouble(key + ".z", 0.0);
            mobOffsets.put(key, new Vector(x, y, z));
        }
        getLogger().info(() -> "Loaded " + mobOffsets.size() + " custom mob offsets.");
    }

    public Map<String, Vector> getMobOffsets() {
        return mobOffsets;
    }

    public int getMaxSkinIndex() {
        return maxSkinIndex;
    }

    private void updateMaxSkinIndex() {
        File dir = new File(getDataFolder(), "images");
        if (!dir.exists() && !dir.mkdirs()) {
            maxSkinIndex = 0;
            return;
        }

        int max = 0;
        File[] files = dir.listFiles((f, n) -> n.startsWith("normal") && n.endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                int len = name.length();
                int val = 0;
                boolean hasDigit = false;
                for (int i = 0; i < len; i++) {
                    char c = name.charAt(i);
                    if (c >= '0' && c <= '9') {
                        hasDigit = true;
                        val = val * 10 + (c - '0');
                    }
                }
                if (hasDigit && val > max) max = val;
            }
        }
        maxSkinIndex = max;
        int finalMax = max;
        getLogger().info(() -> "Max damage skin index cached: " + finalMax);
    }

    public void reloadPlugin() {
        reloadConfig();
        if (renderer != null) {
            try {
                renderer.removeAll();
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Renderer cleanup error on reload", t);
            }
        }

        loadMobOffsets();
        updateMaxSkinIndex();

        try {
            // 저장 요청 후 새 매니저로 교체
            if (blacklistManager != null) {
                var m = BlacklistManager.class.getMethod("saveIfDirtyAsync");
                var ret = m.invoke(blacklistManager);
                if (ret instanceof CompletableFuture) {
                    //noinspection unchecked
                    ((CompletableFuture<Void>) ret).get(2, TimeUnit.SECONDS);
                }
            }
        } catch (Exception ignored) {
        }

        blacklistManager = new BlacklistManager(this, getDataFolder());
        renderer = new DamageDisplayRendererImpl(this);

        getLogger().info(() -> "DamageDisplay fully reloaded.");
    }

    public BlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    // 외부 리스너에서 사용할 전용 I O 실행기 접근자
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }
}
