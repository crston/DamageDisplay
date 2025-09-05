package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Map<UUID, CachedAura> auraCache = new ConcurrentHashMap<>();
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    // [핵심] 모든 애니메이션을 관리할 단일 리스트
    private final List<AnimatedDisplay> activeDisplays = new ArrayList<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
        // [핵심] 단 하나의 BukkitRunnable을 시작하여 모든 애니메이션을 관리
        startAnimationTask();
    }

    @Override
    public void display(Location loc, int damage, boolean isCritical, int skinIndex, double[] offset) {
        if (damage <= 0) return;
        Location displayLoc = loc.clone().add(offset[0], offset[1], offset[2]);

        if (useTextDisplay) {
            spawnAndTrack(displayLoc, damage, isCritical, skinIndex);
        } else {
            showArmorStand(displayLoc, damage, isCritical, skinIndex);
        }
    }

    // 엔티티를 생성하고 추적 리스트에 추가하는 메소드
    private void spawnAndTrack(Location loc, int damage, boolean isCritical, int skinIndex) {
        if (loc.getWorld() == null) return;

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setShadowed(false);
            d.setSeeThrough(true);
            d.setPersistent(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTextOpacity((byte) -1);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            d.setInterpolationDuration(1); // 보간을 비활성화하여 즉각적인 움직임
            d.setInterpolationDelay(-1);
            d.text(buildComponent(damage, isCritical, skinIndex));
        });

        // [핵심] 새로운 작업을 생성하는 대신, 관리 리스트에 추가만 함
        activeDisplays.add(new AnimatedDisplay(display));
    }

    // 단 하나의 Task가 모든 애니메이션을 처리하는 가장 안정적인 방식
    private void startAnimationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDisplays.isEmpty()) return;

                // Iterator를 사용하여 안전하게 리스트 순회 및 제거
                Iterator<AnimatedDisplay> iterator = activeDisplays.iterator();
                while (iterator.hasNext()) {
                    AnimatedDisplay display = iterator.next();

                    // 애니메이션 업데이트 및 제거 여부 판단
                    if (display.update()) {
                        // true를 반환하면 애니메이션이 끝났다는 의미
                        iterator.remove(); // 리스트에서 제거
                        if (display.entity.isValid()) {
                            display.entity.remove(); // 엔티티 완전 삭제
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 가장 안전한 동기(Sync) 방식으로 실행
    }

    // (이하 다른 메소드들은 변경 없음)
    public CachedAura getAuraData(Entity damager, Entity victim) {
        UUID damagerId = (damager instanceof LivingEntity) ? damager.getUniqueId() : Util.NIL_UUID;
        long now = System.currentTimeMillis();
        CachedAura cached = auraCache.get(damagerId);
        if (cached != null && (now - cached.timestamp < 1000)) {
            Vector offsetVector = getMythicMobOffset(victim);
            return new CachedAura(cached.isCritical, cached.skinIndex, new double[]{offsetVector.getX(), offsetVector.getY(), offsetVector.getZ()}, cached.timestamp);
        }
        boolean critical = false;
        int skin = 0;
        Vector offsetVector = getMythicMobOffset(victim);
        double[] offset = {offsetVector.getX(), offsetVector.getY(), offsetVector.getZ()};
        try {
            if (damager instanceof Player p) skin = plugin.getPlayerSkin(p.getUniqueId());
            if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && damager instanceof LivingEntity le) {
                SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(le));
                if (caster != null) {
                    if (caster.hasAura("critical")) critical = true;
                    for (int i = 0; i <= plugin.getMaxSkinIndex(); i++) {
                        if (caster.hasAura("damageskin" + i)) { skin = i; break; }
                    }
                    for (int i = -50; i <= 50; i++) {
                        String valStr = String.format(Locale.US, "%.1f", i / 10.0);
                        if (caster.hasAura("displayx" + valStr)) offset[0] = i / 10.0;
                        if (caster.hasAura("displayy" + valStr)) offset[1] = i / 10.0;
                        if (caster.hasAura("displayz" + valStr)) offset[2] = i / 10.0;
                    }
                }
            }
        } catch (Exception ignored) {}
        CachedAura result = new CachedAura(critical, skin, offset, now);
        if(!damagerId.equals(Util.NIL_UUID)) auraCache.put(damagerId, result);
        return result;
    }

    @SuppressWarnings("resource")
    private Vector getMythicMobOffset(Entity entity) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && MythicBukkit.inst().getMobManager().isMythicMob(entity)) {
            MythicMob mm = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity).getType();
            if (mm != null) {
                Vector customOffset = plugin.getMobOffsets().get(mm.getInternalName());
                if (customOffset != null) return customOffset.clone();
            }
        }
        return new Vector(0, entity.getHeight() * 0.8 + 0.5, 0);
    }

    private boolean isTextDisplaySupported() {
        try { Class.forName("org.bukkit.entity.TextDisplay"); return true; }
        catch (ClassNotFoundException e) { return false; }
    }

    private Component buildComponent(int damage, boolean critical, int skin) {
        String fontName = (critical ? "critical" : "normal") + skin;
        Key key = fontKeyCache.computeIfAbsent(fontName, f -> Key.key("damagedisplay", f));
        return Component.text(String.valueOf(damage)).font(key);
    }

    private void showArmorStand(Location loc, int damage, boolean critical, int skin) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false); stand.setGravity(false); stand.setSmall(true); stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.customName(buildComponent(damage, critical, skin));
        Bukkit.getScheduler().runTaskLater(plugin, stand::remove, 12L); // 0.6초
    }

    @Override
    public void removeAll() {
        activeDisplays.forEach(d -> d.entity.remove());
        activeDisplays.clear();
        auraCache.clear();
    }

    // [핵심] 안정적인 애니메이션 로직을 가진 상태 관리 클래스
    private static class AnimatedDisplay {
        private final TextDisplay entity;

        // [수정] 0.6초(12틱) 동안 자연스러운 애니메이션을 위한 값
        private final double velocityY = 0.1;  // 초기 상승 속도
        private final double gravity = 0.02; // 중력
        private final int maxTicks = 12;     // 0.6초

        private int ticksLived = 0;
        private double currentYVelocity = velocityY;

        AnimatedDisplay(TextDisplay entity) {
            this.entity = entity;
        }

        /**
         * 매 틱 호출되어 애니메이션을 처리합니다.
         * @return true를 반환하면 이 애니메이션을 삭제합니다.
         */
        public boolean update() {
            // 엔티티가 유효하지 않거나 시간이 다 되면 즉시 삭제 요청
            if (!entity.isValid() || ticksLived >= maxTicks) {
                return true;
            }

            // 위치 이동
            entity.teleport(entity.getLocation().add(0, currentYVelocity, 0));

            // 다음 틱을 위한 값 계산
            currentYVelocity -= gravity;
            ticksLived++;

            return false; // 아직 애니메이션 진행 중
        }
    }

    public record CachedAura(boolean isCritical, int skinIndex, double[] offset, long timestamp) {}

    private static class Util {
        public static final UUID NIL_UUID = new UUID(0, 0);
    }
}