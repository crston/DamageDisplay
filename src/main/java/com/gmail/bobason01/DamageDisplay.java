package com.gmail.bobason01;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DamageDisplay extends JavaPlugin implements Listener {
    private EntityDamageListener damageListener;
    private BlacklistManager blacklistManager;
    private final Map<UUID, Integer> playerSkins = Collections.synchronizedMap(new HashMap<>()); // 플레이어 스킨만 저장

    @Override
    public void onEnable() {
        loadConfig();
        createResourceFiles();
        blacklistManager = new BlacklistManager(getDataFolder());
        damageListener = new EntityDamageListener(this);
        getServer().getPluginManager().registerEvents(damageListener, this);
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
    }

    private void registerCommands() {
        if (getCommand("damagereload") != null) {
            Objects.requireNonNull(getCommand("damagereload")).setExecutor(this);
        }
        if (getCommand("setdamageskin") != null) {
            Objects.requireNonNull(getCommand("setdamageskin")).setExecutor(this);
        }
        if (getCommand("blacklist") != null) {
            Objects.requireNonNull(getCommand("blacklist")).setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        if (damageListener != null) {
            damageListener.removeAllText(); // 아머스탠드 제거
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        File playerFile = new File(getDataFolder(), "saves/" + playerUUID + ".yml");

        if (!playerFile.exists()) {
            createPlayerFile(playerFile, playerUUID);
        } else {
            loadPlayerSkin(playerUUID, playerFile);
        }
    }

    private void createPlayerFile(File playerFile, UUID playerUUID) {
        CompletableFuture.runAsync(() -> {
            try {
                File parentDir = playerFile.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    getLogger().severe("디렉토리 생성 실패: " + parentDir.getPath());
                    return;
                }
                if (playerFile.createNewFile()) {
                    FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                    playerConfig.set("damage-skin", 0);
                    playerConfig.save(playerFile);
                    playerSkins.put(playerUUID, 0); // 기본 스킨 인덱스를 메모리에 저장
                } else {
                    getLogger().severe("플레이어 저장 파일 생성 실패 " + playerUUID);
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "플레이어 저장 파일 생성 실패 {0}: {1}", new Object[]{playerUUID, e.getMessage()});
            }
        });
    }

    private void loadPlayerSkin(UUID playerUUID, File playerFile) {
        CompletableFuture.runAsync(() -> {
            FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
            int skinIndex = playerConfig.getInt("damage-skin", 0);
            playerSkins.put(playerUUID, skinIndex);
        });
    }

    private void createResourceFiles() {
        ResourceFileCreator resourceFileCreator = new ResourceFileCreator(getDataFolder());
        resourceFileCreator.createResourceFiles();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("damagereload")) {
            long startTime = System.currentTimeMillis();
            reloadPlugin();
            long endTime = System.currentTimeMillis();
            long reloadTime = endTime - startTime;
            sender.sendMessage("플러그인이 " + reloadTime + "ms 안에 성공적으로 리로드되었습니다.");
            return true;
        } else if (command.getName().equalsIgnoreCase("setdamageskin")) {
            if (sender instanceof Player player) {
                if (args.length == 1) {
                    try {
                        int skinIndex = Integer.parseInt(args[0]);
                        savePlayerSkin(player.getUniqueId(), skinIndex);
                        player.sendMessage("데미지 스킨이 " + skinIndex + "(으)로 설정되었습니다.");
                    } catch (NumberFormatException e) {
                        player.sendMessage("잘못된 스킨 인덱스입니다. 숫자를 입력하세요.");
                    }
                } else {
                    player.sendMessage("사용법: /setdamageskin <스킨 인덱스>");
                }
            } else {
                sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("blacklist")) {
            if (!sender.hasPermission("damagedisplay.blacklist")) {
                sender.sendMessage("이 명령어를 사용할 권한이 없습니다.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("사용법: /blacklist <add|remove|list> [entityType]");
                return true;
            }

            String action = args[0].toLowerCase();

            if (action.equals("list")) {
                // List all blacklisted entities
                StringBuilder blacklistedEntities = new StringBuilder("현재 블랙리스트에 등록된 엔티티: ");
                boolean empty = true;

                for (EntityType type : EntityType.values()) {
                    if (blacklistManager.isBlacklisted(type)) {
                        if (!empty) {
                            blacklistedEntities.append(", ");
                        }
                        blacklistedEntities.append(type.name());
                        empty = false;
                    }
                }

                if (empty) {
                    blacklistedEntities.append("없음");
                }

                sender.sendMessage(blacklistedEntities.toString());
                return true;
            }

            String entityTypeStr = args[1].toUpperCase();
            EntityType entityType;

            try {
                entityType = EntityType.valueOf(entityTypeStr);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("유효하지 않은 엔티티 타입입니다: " + entityTypeStr);
                return true;
            }

            if (action.equals("add")) {
                blacklistManager.addToBlacklist(entityType);
                sender.sendMessage(entityType.name() + "이(가) 블랙리스트에 추가되었습니다.");
                return true;
            } else if (action.equals("remove")) {
                blacklistManager.removeFromBlacklist(entityType);
                sender.sendMessage(entityType.name() + "이(가) 블랙리스트에서 제거되었습니다.");
                return true;
            } else {
                sender.sendMessage("유효하지 않은 액션입니다. add, remove, 또는 list를 사용하세요.");
                return true;
            }
        }
        return false;
    }

    private void reloadPlugin() {
        this.reloadConfig();
        this.onDisable();
        this.onEnable();
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        YamlConfiguration.loadConfiguration(configFile);
    }

    private void savePlayerSkin(UUID playerUUID, int skinIndex) {
        CompletableFuture.runAsync(() -> {
            File playerFile = new File(getDataFolder(), "saves/" + playerUUID + ".yml");
            try {
                FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                playerConfig.set("damage-skin", skinIndex);
                playerConfig.save(playerFile);
                playerSkins.put(playerUUID, skinIndex); // 메모리에 저장
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "플레이어 스킨 저장 실패 {0}: {1}", new Object[]{playerUUID, e.getMessage()});
            }
        });
    }

    public int getPlayerSkin(UUID playerUUID) {
        return playerSkins.getOrDefault(playerUUID, 0);
    }

    public int getEntitySkin(Entity entity) {
        if (entity instanceof Player) {
            UUID playerUUID = entity.getUniqueId();
            return getPlayerSkin(playerUUID);
        }
        return 0; // 플레이어가 아닌 경우 기본 스킨 사용
    }

    public boolean isEntityBlacklisted(EntityType entityType) {
        return blacklistManager.isBlacklisted(entityType);
    }
}