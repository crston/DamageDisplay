package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerConnectionListener implements Listener {

    private final DamageDisplay plugin;

    public PlayerConnectionListener(DamageDisplay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.loadPlayerSkinData(event.getPlayer().getUniqueId());
    }
}