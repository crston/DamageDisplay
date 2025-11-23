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

public final class EntityDamageListener implements Listener {

    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRendererImpl renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Damageable)) {
            return;
        }

        if (plugin.isEntityBlacklisted(victim.getType())) {
            return;
        }

        double damage = event.getFinalDamage();
        if (damage <= 0.0) {
            return;
        }

        int shownDamage = (int) (damage + 0.5);
        if (shownDamage <= 0) {
            return;
        }

        Entity damager = event.getDamager();
        Location loc = victim.getLocation();

        DamageDisplayRendererImpl.DamageData data = renderer.buildDamageData(damager, victim, damage);
        double[] offset = data.offset();

        double ox = offset != null && offset.length > 0 ? offset[0] : 0.0;
        double oy = offset != null && offset.length > 1 ? offset[1] : 0.0;
        double oz = offset != null && offset.length > 2 ? offset[2] : 0.0;

        renderer.display(loc, shownDamage, data.critical(), data.skinIndex(), ox, oy, oz);
    }
}
