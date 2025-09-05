package com.gmail.bobason01.listener;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.DamageDisplayRendererImpl;
import com.gmail.bobason01.util.DamageDisplayRenderer;
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

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRenderer renderer) {
        this.plugin = plugin;
        this.renderer = (DamageDisplayRendererImpl) renderer;
    }

    /**
     * 엔티티가 다른 엔티티에 의해 피해를 입었을 때 호출됩니다.
     * @param event EntityDamageByEntityEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // 피해자가 Damageable 인터페이스를 구현하는지 확인
        if (!(event.getEntity() instanceof Damageable)) {
            return;
        }

        Entity target = event.getEntity();

        // 블랙리스트에 등록된 엔티티는 무시
        if (plugin.isEntityBlacklisted(target.getType())) {
            return;
        }

        // 최종 데미지가 0 이하면 표시하지 않음
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) {
            return;
        }

        // 데미지를 정수 형태로 변환 (예: 8.5 데미지는 85로 표시)
        int shownDamage = (int) Math.round(finalDamage * 10);

        Entity damager = event.getDamager();
        Location loc = target.getLocation();

        // 가해자와 피해자 정보를 바탕으로 아우라 데이터(치명타, 스킨, 위치 오프셋)를 가져옴
        DamageDisplayRendererImpl.CachedAura aura = renderer.getAuraData(damager, target);

        // 엔티티 생성/수정은 메인 스레드에서 처리해야 하므로 Bukkit 스케줄러를 사용
        plugin.getServer().getScheduler().runTask(plugin, () -> renderer.display(loc, shownDamage, aura.isCritical(), aura.skinIndex(), aura.offset()));
    }
}