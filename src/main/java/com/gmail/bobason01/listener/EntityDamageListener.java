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
import org.bukkit.metadata.MetadataValue;

import java.util.HashMap;
import java.util.Map;

public final class EntityDamageListener implements Listener {

    // 상수로 선언하여 매 타격마다 새로운 객체를 생성하는 낭비를 없앱니다
    private static final String META_CUSTOM = "CUSTOM_SKILL_DAMAGE";
    private static final String META_MMO = "MMOITEMS_SKILL_DAMAGE";

    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    // 무거운 식별자 대신 내부 정수형 아이디를 사용하여 검색 속도를 극대화합니다
    private final Map<Integer, DamageEventData> pendingDamages = new HashMap<>(64);

    // 병합을 위한 내부 클래스
    private static class DamageEventData {
        Entity victim;
        double damage;
        boolean critical;
        int skinIndex;
        double[] offset;
        boolean isCustom;
    }

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRendererImpl renderer) {
        this.plugin = plugin;
        this.renderer = renderer;

        // 일괄 처리 스케줄러 가동
        Bukkit.getScheduler().runTaskTimer(plugin, this::processPendingDamages, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(victim.getType())) return;

        int entityId = victim.getEntityId();

        // 방어력 연산이 끝난 몬스터의 실제 체력 감소량을 무조건 사용합니다
        double finalDamage = event.getFinalDamage();

        // 리스트를 불러오지 않고 존재 여부만 빠르게 확인하여 연산 속도를 높입니다
        boolean isCustom = victim.hasMetadata(META_CUSTOM) || victim.hasMetadata(META_MMO);

        // 미세한 오차 데미지 무시
        if (finalDamage <= 0.01) return;

        var data = renderer.buildDamageData(event, finalDamage);

        DamageEventData existing = pendingDamages.get(entityId);
        if (existing != null) {
            // 이미 이번 틱에 데미지가 등록되어 있다면 논리에 맞게 덮어씌웁니다
            if (isCustom && !existing.isCustom) {
                existing.damage = finalDamage;
                existing.isCustom = true;
            } else {
                existing.damage = Math.max(existing.damage, finalDamage);
            }
            if (data.critical()) {
                existing.critical = true;
            }
        } else {
            // 새로운 데미지 데이터 등록
            DamageEventData newData = new DamageEventData();
            newData.victim = victim;
            newData.damage = finalDamage;
            newData.critical = data.critical();
            newData.skinIndex = data.skinIndex();
            newData.offset = data.offset();
            newData.isCustom = isCustom;
            pendingDamages.put(entityId, newData);
        }
    }

    // 매 틱마다 한 번만 실행되어 모인 데미지들을 화면에 띄우고 메모리를 정리합니다
    private void processPendingDamages() {
        // 처리할 데이터가 없으면 즉시 종료합니다
        if (pendingDamages.isEmpty()) return;

        for (DamageEventData data : pendingDamages.values()) {
            int shown = (int) Math.round(data.damage);

            // 엔티티가 살아있을 때만 홀로그램을 띄웁니다
            if (shown > 0 && data.victim.isValid()) {
                Location loc = data.victim.getLocation();
                renderer.displayWithThrottling(data.victim, loc, shown, data.critical, data.skinIndex, data.offset[0], data.offset[1], data.offset[2]);
            }

            // 남은 메타데이터를 지워줍니다
            clearMetadata(data.victim, META_CUSTOM);
            clearMetadata(data.victim, META_MMO);
        }

        // 처리가 끝난 맵을 비워 다음 틱을 준비합니다
        pendingDamages.clear();
    }

    // 타 플러그인이 남긴 메타데이터를 지울 때는 반드시 꼬리표를 생성한 플러그인의 권한을 받아와서 넘겨야 지워집니다
    private void clearMetadata(Entity entity, String key) {
        if (entity.hasMetadata(key)) {
            for (MetadataValue value : entity.getMetadata(key)) {
                entity.removeMetadata(key, value.getOwningPlugin());
            }
        }
    }
}