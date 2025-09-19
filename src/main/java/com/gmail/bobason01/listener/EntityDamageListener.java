package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamageListener implements Listener {
    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRendererImpl renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Damageable)) {
            return;
        }

        Entity target = event.getEntity();
        if (plugin.isEntityBlacklisted(target.getType())) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) {
            return;
        }

        int shownDamage = (int) Math.round(finalDamage);
        if (shownDamage <= 0) {
            return;
        }

        Entity damager = event.getDamager();
        Location loc = target.getLocation();

        DamageDisplayRendererImpl.DamageData data = renderer.getDamageData(damager, target);

        plugin.getServer().getScheduler().runTask(plugin, () -> renderer.display(loc, shownDamage, data.isCritical(), data.skinIndex(), data.offset()));
    }
}