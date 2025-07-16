package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import com.gmail.bobason01.util.DamageDisplayRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
        EntityType type = target.getType();

        if (!(target instanceof Damageable damageable)
                || event.isCancelled()
                || plugin.isEntityBlacklisted(type)) return;

        double beforeHealth = damageable.getHealth();
        Entity damager = event.getDamager();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid()) return;
            if (!(target instanceof Damageable dmgTarget)) return;

            double afterHealth = dmgTarget.getHealth();
            if (afterHealth >= beforeHealth) return;

            int shownDamage = (int) (beforeHealth - afterHealth);
            DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager);
            Location loc = target.getLocation();
            renderer.display(loc, shownDamage, aura.isCritical(), aura.skinIndex(), aura.offset());
        });
    }
}
