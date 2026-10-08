package com.gmail.bobason01.api;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import java.util.UUID;

public final class DamageDisplayAPI {

    private static DamageDisplay plugin;

    private DamageDisplayAPI() {}

    public static void init(DamageDisplay instance) {
        plugin = instance;
    }

    public static void displayDamage(Location location, int damage, boolean critical, int skinIndex, double offsetX, double offsetY, double offsetZ) {
        if (plugin != null && plugin.getRenderer() != null) {
            plugin.getRenderer().display(location, damage, critical, skinIndex, offsetX, offsetY, offsetZ);
        }
    }

    public static void displayDamageWithOffset(Entity victim, Location location, int damage, boolean critical, int skinIndex) {
        if (plugin != null && plugin.getRenderer() != null) {
            org.bukkit.util.Vector off = plugin.getMobOffset(victim);
            plugin.getRenderer().displayWithThrottling(victim, location, damage, critical, skinIndex, off.getX(), off.getY(), off.getZ());
        }
    }

    public static int getPlayerSkin(UUID uuid) {
        return plugin != null ? plugin.getPlayerSkin(uuid) : 0;
    }

    public static void setPlayerSkin(UUID uuid, int skinIndex) {
        if (plugin != null) {
            plugin.saveSkin(uuid, skinIndex);
        }
    }

    public static boolean isEntityBlacklisted(EntityType type) {
        return plugin != null && plugin.isEntityBlacklisted(type);
    }

    public static void reload() {
        if (plugin != null) {
            plugin.reloadPlugin();
        }
    }
}