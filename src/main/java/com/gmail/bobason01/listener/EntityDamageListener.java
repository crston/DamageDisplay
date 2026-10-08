package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import com.gmail.bobason01.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class EntityDamageListener implements Listener {

    private final DamageDisplay plugin;

    private final ConcurrentHashMap<Integer, DamageEventData> pendingDamages = new ConcurrentHashMap<>(128);
    private final ConcurrentLinkedQueue<DamageEventData> dataPool = new ConcurrentLinkedQueue<>();

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
    }

    private DamageDisplayRendererImpl renderer() {
        return plugin.getRenderer();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        final Entity victim = event.getEntity();

        if (!(victim instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(victim.getType())) return;

        final double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.01) return;

        final int entityId = victim.getEntityId();
        final DamageDisplayRendererImpl renderer = renderer();
        if (renderer == null) return;

        pendingDamages.compute(entityId, (id, data) -> {
            if (data == null) {
                data = dataPool.poll();
                if (data == null) data = new DamageEventData();

                var renderData = renderer.buildDamageData(event, finalDamage);
                data.victim = victim;
                data.hitLocation = victim.getLocation();
                data.damage = finalDamage;
                data.critical = renderData.critical();
                data.skinIndex = renderData.skinIndex();
                data.offset = renderData.offset();

                SchedulerUtil.runEntityTaskLater(plugin, victim, () -> processPendingDamage(entityId), 1L);
            } else {
                data.damage += finalDamage;
                var renderData = renderer.buildDamageData(event, finalDamage);
                if (renderData.critical()) {
                    data.critical = true;
                }
            }
            return data;
        });
    }

    private void processPendingDamage(int entityId) {
        DamageEventData data = pendingDamages.remove(entityId);
        if (data == null) return;

        final DamageDisplayRendererImpl renderer = renderer();
        final int shown = (int) Math.round(data.damage);
        if (shown > 0 && renderer != null) {
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
        dataPool.offer(data);
    }
}