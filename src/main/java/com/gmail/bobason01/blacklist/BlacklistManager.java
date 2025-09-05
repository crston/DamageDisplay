package com.gmail.bobason01.blacklist;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BlacklistManager {
    private static final Logger LOGGER = Logger.getLogger(BlacklistManager.class.getName());

    private final Set<EntityType> blacklist = ConcurrentHashMap.newKeySet();
    private final File blacklistFile;
    private final FileConfiguration blacklistConfig;
    private final Plugin plugin;
    private volatile boolean dirty = false;

    public BlacklistManager(Plugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.blacklistFile = new File(dataFolder, "blacklist.yml");
        this.blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
        loadBlacklist();
        startAutoSaveTask();
    }

    /**
     * 특정 엔티티 유형을 블랙리스트에 추가합니다.
     * @param type 블랙리스트에 추가할 EntityType
     */
    public void addToBlacklist(EntityType type) {
        if (blacklist.add(type)) {
            dirty = true;
        }
    }

    /**
     * 특정 엔티티 유형을 블랙리스트에서 제거합니다.
     * @param type 블랙리스트에서 제거할 EntityType
     */
    public void removeFromBlacklist(EntityType type) {
        if (blacklist.remove(type)) {
            dirty = true;
        }
    }

    /**
     * 해당 엔티티 유형이 블랙리스트에 포함되어 있는지 확인합니다.
     * @param type 확인할 EntityType
     * @return 블랙리스트 포함 여부
     */
    public boolean isBlacklisted(EntityType type) {
        return blacklist.contains(type);
    }

    /**
     * 현재 블랙리스트에 등록된 모든 엔티티 유형의 Set을 반환합니다.
     * @return 블랙리스트 Set
     */
    public Set<EntityType> getBlacklisted() {
        return blacklist;
    }

    /**
     * blacklist.yml 파일에서 블랙리스트 정보를 불러옵니다.
     */
    private void loadBlacklist() {
        blacklist.clear();
        List<String> names = blacklistConfig.getStringList("blacklist");
        for (String name : names) {
            try {
                // 대소문자 구분 없이 처리
                blacklist.add(EntityType.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid EntityType in blacklist: " + name);
            }
        }
    }

    /**
     * 블랙리스트에 변경 사항이 있을 경우, 비동기적으로 파일에 저장합니다.
     */
    public void saveIfDirtyAsync() {
        if (!dirty) return;
        dirty = false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> entityNames = blacklist.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            blacklistConfig.set("blacklist", entityNames);
            try {
                blacklistConfig.save(blacklistFile);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to save blacklist", e);
            }
        });
    }

    /**
     * 주기적으로 블랙리스트 변경 사항을 파일에 저장하는 비동기 작업을 시작합니다.
     */
    private void startAutoSaveTask() {
        // 5분(6000 ticks) 마다 저장
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfDirtyAsync, 6000L, 6000L);
    }
}