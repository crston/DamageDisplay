package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;

public final class EntityDamageListener implements Listener {

    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    private final Map<Integer, DamageEventData> pendingDamages = new HashMap<>(128, 0.75f);
    private final ArrayDeque<DamageEventData> dataPool = new ArrayDeque<>(128);

    private static class DamageEventData {
        Entity victim;
        Location hitLocation;
        double damage;
        boolean critical;
        int skinIndex;
        double[] offset;

        void reset() {
            this.victim = null;
            this.hitLocation = null;
            this.damage = 0;
            this.critical = false;
            this.skinIndex = 0;
            this.offset = null;
        }
    }

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRendererImpl renderer) {
        this.plugin = plugin;
        this.renderer = renderer;

        Bukkit.getScheduler().runTaskTimer(plugin, this::processPendingDamages, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        final Entity victim = event.getEntity();

        if (!(victim instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(victim.getType())) return;

        final double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.01) return;

        final int entityId = victim.getEntityId();
        DamageEventData data = pendingDamages.get(entityId);

        if (data != null) {
            // 한 틱 내에 여러 번 데미지가 들어올 경우 합산 처리 (성능 최적화)
            data.damage += finalDamage;

            var renderData = renderer.buildDamageData(event, finalDamage);
            if (renderData.critical()) {
                data.critical = true;
            }
        } else {
            data = dataPool.isEmpty() ? new DamageEventData() : dataPool.pop();
            var renderData = renderer.buildDamageData(event, finalDamage);

            data.victim = victim;
            data.hitLocation = victim.getLocation();
            data.damage = finalDamage;
            data.critical = renderData.critical();
            data.skinIndex = renderData.skinIndex();
            data.offset = renderData.offset();

            pendingDamages.put(entityId, data);
        }
    }

    private void processPendingDamages() {
        if (pendingDamages.isEmpty()) return;

        for (DamageEventData data : pendingDamages.values()) {
            final int shown = (int) Math.round(data.damage);

            if (shown > 0) {
                renderer.displayWithThrottling(
                        data.victim,
                        data.hitLocation,
                        shown,
                        data.critical,
                        data.skinIndex,
                        data.offset[0],
                        data.offset[1],
                        data.offset[2]
                );
            }

            data.reset();
            dataPool.push(data);
        }

        pendingDamages.clear();
    }
}