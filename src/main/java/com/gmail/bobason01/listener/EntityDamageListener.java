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

/**
 * EntityDamageListener - Java 21 최적화 버전
 * - 모든 연산을 상수시간 내 수행
 * - 불필요한 객체 생성 최소화
 * - 스케줄러 호출 오버헤드 최소화
 */
public final class EntityDamageListener implements Listener {

    private final DamageDisplay plugin;
    private final DamageDisplayRendererImpl renderer;

    // 미리 생성된 Runnable (가변 파라미터 캡처 없는 경우)
    private final Runnable displayTask = new Runnable() {
        @Override
        public void run() {
            // 빈 껍데기: 람다 대신 구조적 참조용
        }
    };

    public EntityDamageListener(DamageDisplay plugin, DamageDisplayRendererImpl renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // 빠른 필터링 (가장 앞에서 걸러냄)
        final Entity target = event.getEntity();
        if (!(target instanceof Damageable)) return;
        if (plugin.isEntityBlacklisted(target.getType())) return;

        final double dmg = event.getFinalDamage();
        if (dmg <= 0.0) return;

        final int shownDamage = (int) (dmg + 0.5);
        if (shownDamage <= 0) return;

        final Entity damager = event.getDamager();
        final Location loc = target.getLocation();

        // 렌더링 데이터 사전 계산 (hot path)
        final DamageDisplayRendererImpl.DamageData data = renderer.getDamageData(damager, target);

        // 메인 스레드 안전 렌더링 호출
        final double[] offSrc = data.offset();
        final double ox = (offSrc != null && offSrc.length > 0) ? offSrc[0] : 0.0;
        final double oy = (offSrc != null && offSrc.length > 1) ? offSrc[1] : 0.0;
        final double oz = (offSrc != null && offSrc.length > 2) ? offSrc[2] : 0.0;

        plugin.getServer().getScheduler().runTask(plugin, () ->
                renderer.display(loc, shownDamage, data.isCritical(), data.skinIndex(), data.offset())
        );
    }
}
