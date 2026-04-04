package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageDisplayRendererImpl implements DamageDisplayRenderer {

    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRenderTimes = new ConcurrentHashMap<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "damage_display");
    }

    public void displayWithThrottling(Entity victim, Location location, int damage, boolean critical, int skinIndex, double ox, double oy, double oz) {
        if (damage <= 0 || location.getWorld() == null) return;

        long now = System.currentTimeMillis();
        UUID victimId = victim.getUniqueId();
        if (lastRenderTimes.getOrDefault(victimId, 0L) > now - 50) return;
        lastRenderTimes.put(victimId, now);

        Location loc = location.clone().add(ox, oy, oz);
        loc.add((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3);

        if (useTextDisplay) spawnOptimizedTextDisplay(loc, damage, critical, skinIndex);
        else spawnLegacyArmorStand(loc, damage, critical, skinIndex);
    }

    @Override
    public void display(Location location, int damage, boolean critical, int skinIndex, double ox, double oy, double oz) {
        if (damage <= 0 || location.getWorld() == null) return;
        Location loc = location.clone().add(ox, oy, oz);
        if (useTextDisplay) spawnOptimizedTextDisplay(loc, damage, critical, skinIndex);
        else spawnLegacyArmorStand(loc, damage, critical, skinIndex);
    }

    private void spawnOptimizedTextDisplay(Location loc, int damage, boolean critical, int skinIndex) {
        int mode = plugin.getAnimationMode();
        int duration = plugin.getAnimationDuration();

        loc.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setPersistent(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            display.text(buildComponent(damage, critical, skinIndex));

            setInitialState(display, mode, damage);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (display.isValid()) playAnimation(display, mode, damage, duration); }, 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (display.isValid()) display.remove(); }, duration + 10L);
        });
    }

    private void setInitialState(TextDisplay display, int mode, int damage) {
        float scale = Math.min(plugin.getScaleMax(), plugin.getScaleBase() + (damage * plugin.getScalePerDamage()));
        switch (mode) {
            case 1 -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 2 -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(0.1f, 0.1f, 0.1f), new AxisAngle4f()));
            case 3 -> display.setTransformation(new Transformation(new Vector3f(0, -0.5f, 0), new AxisAngle4f(), new Vector3f(1.5f, 0.1f, 1.5f), new AxisAngle4f()));
            case 5 -> display.setTransformation(new Transformation(new Vector3f(0, 3.0f, 0), new AxisAngle4f(), new Vector3f(scale, scale * 2.0f, scale), new AxisAngle4f()));
            case 6 -> display.setTransformation(new Transformation(new Vector3f(0, 0.2f, 0), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
            case 7 -> {
                float side = ThreadLocalRandom.current().nextBoolean() ? -1.0f : 1.0f;
                display.setTransformation(new Transformation(new Vector3f(side, 0, 0), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            }
            case 9, 10 -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            default -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
        }
    }

    private void playAnimation(TextDisplay display, int mode, int damage, int duration) {
        display.setInterpolationDuration(duration);
        display.setInterpolationDelay(0);
        float scale = Math.min(plugin.getScaleMax(), plugin.getScaleBase() + (damage * plugin.getScalePerDamage()));
        switch (mode) {
            case 1 -> display.setTransformation(new Transformation(new Vector3f(0, 2.0f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 2 -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                display.setTransformation(new Transformation(new Vector3f((float) (r.nextDouble() - 0.5) * 3, (float) r.nextDouble() * 1.5f + 0.5f, (float) (r.nextDouble() - 0.5) * 3), new AxisAngle4f((float) Math.toRadians(r.nextInt(360)), r.nextFloat(), r.nextFloat(), r.nextFloat()), new Vector3f(1, 1, 1), new AxisAngle4f()));
            }
            case 3 -> display.setTransformation(new Transformation(new Vector3f(0, 1.5f, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
            case 4 -> display.setTransformation(new Transformation(new Vector3f(0, 3.0f, 0), new AxisAngle4f((float) Math.toRadians(1080), 0, 1, 0), new Vector3f(0.1f, 0.1f, 0.1f), new AxisAngle4f()));
            case 5 -> display.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 6 -> display.setTransformation(new Transformation(new Vector3f(0, 2.0f, -0.5f), new AxisAngle4f((float) Math.toRadians(-360), 1, 0, 0), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
            case 7 -> {
                float ex = display.getTransformation().getTranslation().x < 0 ? 1.0f : -1.0f;
                display.setTransformation(new Transformation(new Vector3f(ex, 1.5f, 0), new AxisAngle4f((float) Math.toRadians(ex > 0 ? 15 : -15), 0, 0, 1), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
            }
            case 8 -> display.setTransformation(new Transformation(new Vector3f(0, 3.5f, 0), new AxisAngle4f(), new Vector3f(scale * 0.5f, scale * 3.0f, scale * 0.5f), new AxisAngle4f()));
            case 9 -> {
                int h = duration / 2;
                display.setInterpolationDuration(h);
                display.setTransformation(new Transformation(new Vector3f(0, 1.5f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) {
                        display.setInterpolationDuration(h);
                        display.setTransformation(new Transformation(new Vector3f(0, -0.5f, 0), new AxisAngle4f(), new Vector3f(scale * 0.8f, scale * 0.8f, scale * 0.8f), new AxisAngle4f()));
                    }
                }, h);
            }
            case 10 -> {
                int rt = (int) (duration * 0.3);
                int lt = duration - rt;
                display.setInterpolationDuration(rt);
                display.setTransformation(new Transformation(new Vector3f(0, 0.4f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) {
                        display.setInterpolationDuration(lt);
                        display.setTransformation(new Transformation(new Vector3f(0, 0.05f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                    }
                }, rt);
            }
            default -> display.setTransformation(new Transformation(new Vector3f(0, 1.0f, 0), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
        }
    }

    private void spawnLegacyArmorStand(Location loc, int damage, boolean critical, int skinIndex) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            s.setCustomName(Integer.toString(damage));
            s.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, stand::remove, plugin.getAnimationDuration());
    }

    private Component buildComponent(int damage, boolean critical, int skinIndex) {
        int clamped = Math.max(0, Math.min(skinIndex, plugin.getMaxSkinIndex()));
        String name = (critical ? "critical" : "normal") + clamped;
        Key key = fontKeyCache.computeIfAbsent(name, k -> Key.key("damagedisplay", k));
        return Component.text(Integer.toString(damage)).font(key);
    }

    private boolean isTextDisplaySupported() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public DamageData buildDamageData(EntityDamageByEntityEvent event, double baseDamage) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        int skin = 0;
        if (damager instanceof Player p) skin = plugin.getPlayerSkin(p.getUniqueId());

        boolean crit = false;

        // 1. MythicMobs Aura 체크 (기존 유지)
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && damager instanceof LivingEntity le) {
            try {
                SkillCaster sc = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(le));
                if (sc != null && sc.hasAura("critical")) crit = true;
            } catch (Exception ignored) {}
        }

        // 2. MythicLib (MMOItems 기반) Critical Check
        if (!crit && plugin.isUseMMOItemsCritical() && Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            try {
                io.lumine.mythic.lib.damage.AttackMetadata attackMeta = io.lumine.mythic.lib.MythicLib.plugin.getDamage().findAttack(event);

                if (attackMeta != null) {
                    io.lumine.mythic.lib.damage.DamageMetadata damageMeta = attackMeta.getDamage();
                    if (damageMeta.isWeaponCriticalStrike() || damageMeta.isSkillCriticalStrike()) {
                        crit = true;
                    }
                }
            } catch (Throwable ignored) {
                // 혹시 모를 런타임 에러 방지용
            }
        }

        Vector off = plugin.getMobOffset(victim);
        return new DamageData(crit, skin, new double[]{off.getX(), off.getY(), off.getZ()}, baseDamage);
    }

    @Override
    public void removeAll() {
        lastRenderTimes.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.INTEGER)) e.remove();
            }
        }
    }

    public record DamageData(boolean critical, int skinIndex, double[] offset, double baseDamage) {}
}