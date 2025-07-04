package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import com.gmail.bobason01.util.DamageDisplayRenderer;
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
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(target.getType())) return;

        double damage = event.getFinalDamage();
        if (damage <= 0) return;

        Entity damager = event.getDamager();
        Location displayLocation = target.getLocation().add(0, 2, 0);

        int shownDamage = (int) Math.round(damage);
        DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager);
        renderer.display(displayLocation, shownDamage, aura.isCritical(), aura.skinIndex());
    }
}
