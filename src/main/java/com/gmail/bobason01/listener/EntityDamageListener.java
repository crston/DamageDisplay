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
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof Damageable damageable)) return;
        if (plugin.isEntityBlacklisted(target.getType())) return;

        double before = damageable.getHealth();
        Entity damager = event.getDamager();
        Location location = target.getLocation().add(0, 2, 0);
        int rawDamage = (int) Math.round(event.getFinalDamage());
        if (rawDamage <= 0) return;

        // 메인 스레드에서 실제 렌더링 처리
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (damageable.getHealth() >= before) return;

            // 캐시된 오라 정보 사용
            DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager);
            int skinIndex = aura.skinIndex();
            boolean isCritical = aura.isCritical();

            renderer.display(location, rawDamage, isCritical, skinIndex);
        });
    }
}
