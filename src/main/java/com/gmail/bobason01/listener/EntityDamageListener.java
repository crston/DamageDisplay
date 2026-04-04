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
import org.bukkit.metadata.MetadataValue;

import java.util.List;

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
        if (!(victim instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(victim.getType())) return;

        // 기본적으로는 getDamage(방어력 적용 전 원본 데미지)를 사용합니다.
        // MMOItems 스킬 표시 수치는 보통 방어력 계산 전 수치를 기준으로 하기 때문입니다.
        double damage = event.getDamage();

        // MMOItems/MythicLib이 엔티티(victim)에 데미지 수치를 메타데이터로 남겼는지 확인합니다.
        // 이벤트 객체가 아니라 victim 엔티티에서 메타데이터를 가져와야 합니다.
        if (victim.hasMetadata("MMOITEMS_SKILL_DAMAGE")) {
            List<MetadataValue> values = victim.getMetadata("MMOITEMS_SKILL_DAMAGE");
            if (!values.isEmpty()) {
                damage = values.get(0).asDouble();
            }
        }

        if (damage <= 0.0) return;

        // 정수 표시를 위해 반올림 처리
        int shown = (int) Math.round(damage);
        if (shown <= 0) return;

        Location loc = victim.getLocation();
        var data = renderer.buildDamageData(event, damage);
        renderer.displayWithThrottling(victim, loc, shown, data.critical(), data.skinIndex(), data.offset()[0], data.offset()[1], data.offset()[2]);
    }
}