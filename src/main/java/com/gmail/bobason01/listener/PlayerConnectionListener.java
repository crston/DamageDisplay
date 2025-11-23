package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PlayerConnectionListener implements Listener {

    private final DamageDisplay plugin;

    public PlayerConnectionListener(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        CompletableFuture.runAsync(() -> {
            try {
                plugin.loadPlayerSkinData(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load skin for " + uuid, e);
            }
        }, plugin.getIoExecutor());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.unloadPlayerSkinData(uuid);
    }
}
