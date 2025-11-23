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
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DamageDisplayRendererImpl implements DamageDisplayRenderer {

    private final DamageDisplay plugin;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;

    private final List<AnimatedDisplay> activeDisplays = new ArrayList<>(512);
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    private static final double RENDER_DISTANCE_SQ = 4096.0;

    private static final int MAX_TICKS = 10;        // 더 빠르게 사라지도록
    private static final int UPDATE_INTERVAL = 1;  // 매 tick 업데이트

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "damage_display");
        startAnimationTask();
    }

    @Override
    public void display(Location location, int damage, boolean critical, int skinIndex,
                        double offsetX, double offsetY, double offsetZ) {
        if (damage <= 0) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Location loc = location.clone().add(offsetX, offsetY, offsetZ);

        if (useTextDisplay) {
            spawnTextDisplay(loc, damage, critical, skinIndex);
        } else {
            spawnArmorStand(loc, damage, critical, skinIndex);
        }
    }

    private void spawnTextDisplay(Location loc, int damage, boolean critical, int skinIndex) {
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setPersistent(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setTextOpacity((byte) 255);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);
            d.text(buildComponent(damage, critical, skinIndex));
        });

        synchronized (activeDisplays) {
            activeDisplays.add(new AnimatedDisplay(display));
        }
    }

    private void spawnArmorStand(Location loc, int damage, boolean critical, int skinIndex) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(true);
            s.setMarker(true);
            s.setCustomNameVisible(true);
            try {
                s.customName(buildComponent(damage, critical, skinIndex));
            } catch (Throwable t) {
                s.setCustomName(Integer.toString(damage));
            }
        });

        synchronized (activeDisplays) {
            activeDisplays.add(new AnimatedDisplay(stand));
        }
    }

    private Component buildComponent(int damage, boolean critical, int skinIndex) {
        int clampedSkin = Math.max(0, Math.min(skinIndex, plugin.getMaxSkinIndex()));
        String fontName = (critical ? "critical" : "normal") + clampedSkin;
        Key key = fontKeyCache.computeIfAbsent(fontName, f -> Key.key("damagedisplay", f));
        return Component.text(Integer.toString(damage)).font(key);
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
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return false;
        }
        if (!(damager instanceof LivingEntity living)) {
            return false;
        }
        try {
            SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(living));
            if (caster != null && caster.hasAura("critical")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public DamageData buildDamageData(Entity damager, Entity victim, double baseDamage) {
        int skinIndex = 0;
        boolean critical = isCritical(damager);
        if (damager instanceof Player player) {
            skinIndex = plugin.getPlayerSkin(player.getUniqueId());
        }

        var offsetVec = plugin.getMobOffset(victim);
        double[] offset = {offsetVec.getX(), offsetVec.getY(), offsetVec.getZ()};
        return new DamageData(critical, skinIndex, offset, baseDamage);
    }

    private void startAnimationTask() {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (tick % UPDATE_INTERVAL != 0) {
                    return;
                }

                synchronized (activeDisplays) {
                    if (activeDisplays.isEmpty()) {
                        return;
                    }

                    for (int i = activeDisplays.size() - 1; i >= 0; i--) {
                        AnimatedDisplay ad = activeDisplays.get(i);
                        if (ad == null) {
                            activeDisplays.remove(i);
                            continue;
                        }

                        if (updateDisplay(ad)) {
                            activeDisplays.remove(i);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean updateDisplay(AnimatedDisplay ad) {
        Entity entity = ad.entity;
        if (entity == null || !entity.isValid()) {
            return true;
        }

        if (ad.age++ >= MAX_TICKS) {
            entity.remove();
            return true;
        }

        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            entity.remove();
            return true;
        }

        boolean anyViewer = world.getPlayers().stream()
                .anyMatch(p -> p.getLocation().distanceSquared(loc) <= RENDER_DISTANCE_SQ);

        if (!anyViewer) {
            entity.remove();
            return true;
        }

        ad.velocityY -= 0.03;
        loc.add(0.0, ad.velocityY, 0.0);
        entity.teleport(loc);

        return false;
    }

    @Override
    public void removeAll() {
        synchronized (activeDisplays) {
            for (AnimatedDisplay ad : activeDisplays) {
                if (ad != null && ad.entity != null && ad.entity.isValid()) {
                    ad.entity.remove();
                }
            }
            activeDisplays.clear();
        }
    }

    private static final class AnimatedDisplay {
        final Entity entity;
        double velocityY = 0.22;   // 더 빠르게 상승
        int age = 0;

        AnimatedDisplay(Entity entity) {
            this.entity = entity;
        }
    }

    public record DamageData(boolean critical, int skinIndex, double[] offset, double baseDamage) {
    }

    private String stripColor(String text) {
        return ChatColor.stripColor(text);
    }
}
