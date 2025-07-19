package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import com.gmail.bobason01.util.DamageDisplayRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamageListener implements Listener {
    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRenderer renderer) {
        this.plugin = plugin;
        this.renderer = (DamageDisplayRendererImpl) renderer;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        Entity damager = event.getDamager();

        if (!(target instanceof Damageable)
                || event.isCancelled()
                || plugin.isEntityBlacklisted(target.getType())) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        int shownDamage = (int) Math.round(finalDamage);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Location loc = target.getLocation();
            DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager);

            renderer.display(loc, shownDamage, aura.isCritical(), aura.skinIndex(), aura.offset());
        });
    }
}
