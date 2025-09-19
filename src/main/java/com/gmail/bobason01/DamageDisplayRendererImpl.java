package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();
    private final List<AnimatedDisplay> activeDisplays = new ArrayList<>();
    private static final double RENDER_DISTANCE_SQUARED = 4096;

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
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
            d.setInterpolationDuration(1);
            d.setInterpolationDelay(-1);
            d.text(buildComponent(damage, isCritical, skinIndex));
        });

        activeDisplays.add(new AnimatedDisplay(display));
    }

    private void startAnimationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDisplays.isEmpty()) return;

                Iterator<AnimatedDisplay> iterator = activeDisplays.iterator();
                while (iterator.hasNext()) {
                    AnimatedDisplay display = iterator.next();

                    boolean isAbandoned = display.entity.getWorld().getPlayers().stream()
                            .noneMatch(p -> p.getLocation().distanceSquared(display.entity.getLocation()) < RENDER_DISTANCE_SQUARED);

                    if (display.update() || isAbandoned) {
                        iterator.remove();
                        if (display.entity.isValid()) {
                            display.entity.remove();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isCritical(Entity damager) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs") && damager instanceof LivingEntity livingDamager) {
            try {
                SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(livingDamager));
                if (caster != null && caster.hasAura("critical")) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public DamageData getDamageData(Entity damager, Entity victim) {
        int skin = 0;
        boolean isCritical = isCritical(damager);

        if (damager instanceof Player player) {
            skin = plugin.getPlayerSkin(player.getUniqueId());
        }

        Vector offsetVector = getOffset(victim);
        double[] offset = {offsetVector.getX(), offsetVector.getY(), offsetVector.getZ()};

        return new DamageData(isCritical, skin, offset);
    }

    private Vector getOffset(Entity entity) {
        String entityTypeName = getEntityTypeName(entity);
        Vector customOffset = plugin.getMobOffsets().get(entityTypeName);
        if (customOffset != null) {
            return customOffset.clone();
        }

        return new Vector(0, entity.getHeight() * 0.8 + 0.5, 0);
    }

    private String getEntityTypeName(Entity entity) {
        if (entity.getCustomName() != null) {
            return ChatColor.stripColor(entity.getCustomName());
        }
        return entity.getType().name();
    }

    private boolean isTextDisplaySupported() {
        try {
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Component buildComponent(int damage, boolean critical, int skin) {
        String fontName = (critical ? "critical" : "normal") + skin;
        Key key = fontKeyCache.computeIfAbsent(fontName, f -> Key.key("damagedisplay", f));
        return Component.text(String.valueOf(damage)).font(key);
    }

    private void showArmorStand(Location loc, int damage, boolean critical, int skin) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            s.customName(buildComponent(damage, critical, skin));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, stand::remove, 12L);
    }

    @Override
    public void removeAll() {
        activeDisplays.forEach(d -> {
            if (d.entity.isValid()) d.entity.remove();
        });
        activeDisplays.clear();
    }

    private static class AnimatedDisplay {
        private final TextDisplay entity;
        private final double velocityY = 0.1;
        private final double gravity = 0.02;
        private final int maxTicks = 12;
        private int ticksLived = 0;
        private double currentYVelocity = velocityY;

        AnimatedDisplay(TextDisplay entity) {
            this.entity = entity;
        }

        public boolean update() {
            if (!entity.isValid() || ticksLived >= maxTicks) {
                return true;
            }
            entity.teleport(entity.getLocation().add(0, currentYVelocity, 0));
            currentYVelocity -= gravity;
            ticksLived++;
            return false;
        }
    }

    public record DamageData(boolean isCritical, int skinIndex, double[] offset) {
    }
}