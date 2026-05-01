package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageDisplayRendererImpl implements DamageDisplayRenderer {

    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "damage_display");
    }

    public void displayWithThrottling(Entity victim, Location location, int damage, boolean critical, int skinIndex, double ox, double oy, double oz) {
        if (damage <= 0 || location.getWorld() == null) return;
        Location loc = location.clone().add(ox, oy, oz);
        loc.add((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.25, 0, (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.25);
        display(loc, damage, critical, skinIndex, 0, 0, 0);
    }

    @Override
    public void display(Location location, int damage, boolean critical, int skinIndex, double ox, double oy, double oz) {
        if (damage <= 0 || location.getWorld() == null) return;
        Location loc = location.clone().add(ox, oy, oz);
        if (useTextDisplay) spawnTextDisplay(loc, damage, critical, skinIndex);
        else spawnLegacyArmorStand(loc, damage, critical);
    }

    private void spawnTextDisplay(Location loc, int damage, boolean critical, int skinIndex) {
        int mode = plugin.getAnimationMode();
        int duration = plugin.getAnimationDuration();
        float scale = calculateScale(damage);

        loc.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            display.text(buildComponent(damage, critical, skinIndex));

            setInitialState(display, mode, scale);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (display.isValid()) playAnimation(display, mode, scale, duration);
            }, 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (display.isValid()) display.remove();
            }, duration + 10L);
        });
    }

    private float calculateScale(int damage) {
        float s = plugin.getScaleBase() + (damage * plugin.getScalePerDamage());
        return Math.max(plugin.getScaleBase(), Math.min(plugin.getScaleMax(), s));
    }

    private void setInitialState(TextDisplay display, int mode, float scale) {
        switch (mode) {
            case 2 -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
            case 3 -> display.setTransformation(new Transformation(new Vector3f(0, -0.6f, 0), new AxisAngle4f(), new Vector3f(scale, 0.01f, scale), new AxisAngle4f()));
            case 5 -> display.setTransformation(new Transformation(new Vector3f(0, 2.5f, 0), new AxisAngle4f(), new Vector3f(scale, scale * 2.0f, scale), new AxisAngle4f()));
            case 7 -> {
                float side = ThreadLocalRandom.current().nextBoolean() ? -0.8f : 0.8f;
                display.setTransformation(new Transformation(new Vector3f(side, 0, 0), new AxisAngle4f(), new Vector3f(scale * 0.5f, scale * 0.5f, scale * 0.5f), new AxisAngle4f()));
            }
            default -> display.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }
    }

    private void playAnimation(TextDisplay display, int mode, float scale, int duration) {
        display.setInterpolationDuration(duration);
        display.setInterpolationDelay(0);
        switch (mode) {
            case 1 -> display.setTransformation(new Transformation(new Vector3f(0, 1.8f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 2 -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                display.setTransformation(new Transformation(new Vector3f((float)(r.nextDouble()-0.5)*2.5f, 1.2f, (float)(r.nextDouble()-0.5)*2.5f), new AxisAngle4f((float)Math.toRadians(r.nextInt(360)), 0,1,0), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            }
            case 3 -> display.setTransformation(new Transformation(new Vector3f(0, 1.2f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 4 -> display.setTransformation(new Transformation(new Vector3f(0, 2.5f, 0), new AxisAngle4f((float)Math.toRadians(1080), 0, 1, 0), new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
            case 5 -> display.setTransformation(new Transformation(new Vector3f(0, 0.3f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            case 6 -> {
                int h = duration / 2;
                display.setInterpolationDuration(h);
                display.setTransformation(new Transformation(new Vector3f(0, 1.0f, -0.3f), new AxisAngle4f((float)Math.toRadians(-170), 1, 0, 0), new Vector3f(scale * 1.1f, scale * 1.1f, scale * 1.1f), new AxisAngle4f()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) {
                        display.setInterpolationDuration(h);
                        display.setTransformation(new Transformation(new Vector3f(0, 1.8f, -0.6f), new AxisAngle4f((float)Math.toRadians(-350), 1, 0, 0), new Vector3f(scale * 1.2f, scale * 1.2f, scale * 1.2f), new AxisAngle4f()));
                    }
                }, h);
            }
            case 7 -> {
                float ex = display.getTransformation().getTranslation().x < 0 ? 0.8f : -0.8f;
                display.setTransformation(new Transformation(new Vector3f(ex, 1.4f, 0), new AxisAngle4f((float)Math.toRadians(ex > 0 ? 15 : -15), 0, 0, 1), new Vector3f(scale * 1.2f, scale * 1.2f, scale * 1.2f), new AxisAngle4f()));
            }
            case 8 -> display.setTransformation(new Transformation(new Vector3f(0, 3.2f, 0), new AxisAngle4f(), new Vector3f(scale * 0.4f, scale * 2.8f, scale * 0.4f), new AxisAngle4f()));
            case 9 -> {
                int h = duration / 2;
                display.setInterpolationDuration(h);
                display.setTransformation(new Transformation(new Vector3f(0, 1.4f, 0), new AxisAngle4f(), new Vector3f(scale * 1.2f, scale * 1.2f, scale * 1.2f), new AxisAngle4f()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) {
                        display.setInterpolationDuration(h);
                        display.setTransformation(new Transformation(new Vector3f(0, 0.4f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                    }
                }, h);
            }
            default -> display.setTransformation(new Transformation(new Vector3f(0, 1.0f, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }
    }

    private void spawnLegacyArmorStand(Location loc, int damage, boolean critical) {
        loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            s.setCustomName((critical ? "§c" : "§f") + damage);
            s.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            Bukkit.getScheduler().runTaskLater(plugin, s::remove, plugin.getAnimationDuration());
        });
    }

    private Component buildComponent(int damage, boolean critical, int skinIndex) {
        int clamped = Math.max(0, Math.min(skinIndex, plugin.getMaxSkinIndex()));
        String name = (critical ? "critical" : "normal") + clamped;
        Key key = fontKeyCache.computeIfAbsent(name, k -> Key.key("damagedisplay", k));
        return Component.text(Integer.toString(damage)).font(key);
    }

    public DamageData buildDamageData(EntityDamageByEntityEvent event, double baseDamage) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        int skin = (damager instanceof Player p) ? plugin.getPlayerSkin(p.getUniqueId()) : 0;
        boolean crit = false;
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && damager instanceof LivingEntity le) {
            try {
                var caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(le));
                if (caster != null && caster.hasAura("critical")) crit = true;
            } catch (Exception ignored) {}
        }
        Vector off = plugin.getMobOffset(victim);
        return new DamageData(crit, skin, new double[]{off.getX(), off.getY(), off.getZ()}, baseDamage);
    }

    private boolean isTextDisplaySupported() {
        try { Class.forName("org.bukkit.entity.TextDisplay"); return true; }
        catch (Exception e) { return false; }
    }

    @Override
    public void removeAll() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.INTEGER)) e.remove();
            }
        }
    }

    public record DamageData(boolean critical, int skinIndex, double[] offset, double baseDamage) {}
}