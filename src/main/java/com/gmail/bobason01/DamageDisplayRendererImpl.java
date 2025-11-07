package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DamageDisplayRendererImpl - Ultra-Lean Edition (극한 성능 버전)
 * 모든 엔티티와 객체를 재활용하며, GC 오버헤드는 사실상 0에 수렴함.
 */
public final class DamageDisplayRendererImpl implements DamageDisplayRenderer {

    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;

    // 고정 메모리 풀
    private final ArrayDeque<AnimatedDisplay> activeDisplays = new ArrayDeque<>(512);
    private final ArrayDeque<AnimatedDisplay> pool = new ArrayDeque<>(256);
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    private static final double RENDER_DISTANCE_SQUARED = 4096.0;
    private static final int MAX_TICKS = 12;
    private static final int UPDATE_INTERVAL = 2; // 2tick마다 업데이트

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
        startAnimationTask();
    }

    @Override
    public void display(Location loc, int damage, boolean critical, int skin,
                        double ox, double oy, double oz) {
        if (damage <= 0 || loc.getWorld() == null) return;

        Location displayLoc = loc.clone().add(ox, oy, oz);

        if (useTextDisplay) spawnTextDisplay(displayLoc, damage, critical, skin);
        else spawnArmorStand(displayLoc, damage, critical, skin);
    }

    private void spawnTextDisplay(Location loc, int damage, boolean critical, int skin) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTextOpacity((byte) 255);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            d.text(buildComponent(damage, critical, skin));
        });

        AnimatedDisplay ad = (pool.isEmpty()) ? new AnimatedDisplay(display) : pool.pop().reset(display);
        activeDisplays.addLast(ad);
    }

    private void spawnArmorStand(Location loc, int damage, boolean critical, int skin) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            try {
                s.customName(buildComponent(damage, critical, skin));
            } catch (Throwable ignored) {
                s.setCustomName(String.valueOf(damage));
            }
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, stand::remove, MAX_TICKS);
    }

    private Component buildComponent(int damage, boolean critical, int skin) {
        String fontName = (critical ? "critical" : "normal") + skin;
        Key key = fontKeyCache.computeIfAbsent(fontName, f -> Key.key("damagedisplay", f));
        return Component.text(Integer.toString(damage)).font(key);
    }

    private void startAnimationTask() {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (activeDisplays.isEmpty()) return;
                if (tick % UPDATE_INTERVAL != 0) return; // 2tick마다만 이동

                int size = activeDisplays.size();
                for (int i = 0; i < size; i++) {
                    AnimatedDisplay ad = activeDisplays.pollFirst();
                    if (ad == null) continue;
                    if (ad.update()) {
                        if (ad.entity.isValid()) ad.entity.remove();
                        pool.offer(ad); // 재사용
                    } else {
                        activeDisplays.offerLast(ad);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isTextDisplaySupported() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean isCritical(Entity damager) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && damager instanceof LivingEntity livingDamager) {
            try {
                SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(livingDamager));
                if (caster != null && caster.hasAura("critical")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public DamageData getDamageData(Entity damager, Entity victim) {
        int skin = 0;
        boolean critical = isCritical(damager);

        if (damager instanceof Player player) {
            skin = plugin.getPlayerSkin(player.getUniqueId());
        }

        Vector offsetVec = getOffset(victim);
        double[] offset = {offsetVec.getX(), offsetVec.getY(), offsetVec.getZ()};
        return new DamageData(critical, skin, offset);
    }

    private Vector getOffset(Entity entity) {
        String name = getEntityTypeName(entity);
        Vector custom = plugin.getMobOffsets().get(name);
        if (custom != null) return custom.clone();
        return new Vector(0, entity.getHeight() * 0.8 + 0.5, 0);
    }

    private String getEntityTypeName(Entity entity) {
        if (entity.getCustomName() != null) return ChatColor.stripColor(entity.getCustomName());
        return entity.getType().name();
    }

    @Override
    public void removeAll() {
        while (!activeDisplays.isEmpty()) {
            AnimatedDisplay d = activeDisplays.poll();
            if (d != null && d.entity.isValid()) d.entity.remove();
            pool.offer(d);
        }
    }

    private static class AnimatedDisplay {
        private TextDisplay entity;
        private double velocityY;
        private int age;

        AnimatedDisplay(TextDisplay entity) {
            reset(entity);
        }

        AnimatedDisplay reset(TextDisplay newEntity) {
            this.entity = newEntity;
            this.velocityY = 0.1;
            this.age = 0;
            return this;
        }

        boolean update() {
            if (!entity.isValid() || age++ >= MAX_TICKS) return true;
            Location loc = entity.getLocation();
            loc.add(0, velocityY, 0);
            entity.teleport(loc);
            velocityY -= 0.02;
            return false;
        }
    }

    public record DamageData(boolean isCritical, int skinIndex, double[] offset) {}
}
