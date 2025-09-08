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

    /**
     * 엔티티가 다른 엔티티에 의해 피해를 입었을 때 호출됩니다.
     * @param event EntityDamageByEntityEvent
     */
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

        // [수정된 부분] 가해자와 피해자 정보를 모두 넘겨줍니다.
        DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager, target);

        plugin.getServer().getScheduler().runTask(plugin, () -> renderer.display(loc, shownDamage, aura.isCritical(), aura.skinIndex(), aura.offset()));
    }
}