package com.gmail.bobason01.util;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class SchedulerUtil {

    private static final boolean isFolia = checkFolia();

    private SchedulerUtil() {}

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void runGlobalTask(DamageDisplay plugin, Runnable task) {
        if (isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runGlobalTaskLater(DamageDisplay plugin, Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runEntityTaskLater(DamageDisplay plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFolia) {
            entity.getScheduler().execute(plugin, task, null, Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runRegionTaskLater(DamageDisplay plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getServer().getRegionScheduler().runDelayed(plugin, location, st -> task.run(), Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
}