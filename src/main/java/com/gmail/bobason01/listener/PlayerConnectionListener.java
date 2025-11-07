package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * PlayerConnectionListener - Java 21 극한 최적화 버전
 * - Virtual Thread Executor 기반 완전 비블로킹 로드/저장
 * - 예외 처리 및 로깅 최적화
 * - 스레드 점유 최소화
 */
public final class PlayerConnectionListener implements Listener {

    private final DamageDisplay plugin;

    public PlayerConnectionListener(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();

        // Virtual Thread I/O → 메인 스레드 완전 비블로킹
        CompletableFuture.runAsync(() -> {
            try {
                plugin.loadPlayerSkinData(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[PlayerData] Failed to load skin for " + uuid, e);
            }
        }, plugin.getIoExecutor());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();

        CompletableFuture.runAsync(() -> {
            try {
                plugin.unloadPlayerSkinData(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[PlayerData] Failed to unload skin for " + uuid, e);
            }
        }, plugin.getIoExecutor());
    }
}
