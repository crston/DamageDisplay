// --- DamageDisplayRendererImpl.java ---
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
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.*;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final String type;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Set<Long> activeDisplayLocations = ConcurrentHashMap.newKeySet();
    private final Queue<TextDisplay> textPool = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> armorPool = new ConcurrentLinkedQueue<>();
    private final Map<UUID, CachedAura> auraCache = new ConcurrentHashMap<>();
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    private volatile double cachedTps = 20.0;
    private static final int LOC_MASK = 0xFFFFF;

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("display.type", "text_display").toLowerCase();
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
        startTpsMonitor();
    }

    @Override
    public void display(Location loc, int damage, boolean isCritical, int skinIndex, double[] offset) {
        if (damage <= 0 || cachedTps < 17.0) return;

        Location displayLoc = loc.clone().add(offset[0], offset[1], offset[2]);
        long key = toKey(displayLoc);
        if (!activeDisplayLocations.add(key)) return;

        if (useTextDisplay) {
            showTextDisplay(displayLoc, damage, isCritical, skinIndex, key);
        } else {
            showArmorStand(displayLoc, damage, isCritical, skinIndex, key);
        }
    }

    public CachedAura getAuraData(Entity damager) {
        if (!(damager instanceof LivingEntity le)) return CachedAura.DEFAULT;

        UUID id = le.getUniqueId();
        long now = System.currentTimeMillis();

        CachedAura cached = auraCache.get(id);
        if (cached != null && now - cached.timestamp < 3000) return cached;

        boolean critical = false;
        int skin = 0;
        double[] offset = {0, 2.0, 0};

        try {
            SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(le));
            if (caster != null) {
                critical = caster.hasAura("critical");

                for (int i = 0; i <= plugin.getMaxSkinIndex(); i++) {
                    if (caster.hasAura("damageskin" + i)) {
                        skin = i;
                        break;
                    }
                }

                for (int i = -50; i <= 50; i++) {
                    double val = i / 10.0;
                    String valStr = String.format(Locale.US, "%.1f", val);
                    if (caster.hasAura("displayx" + valStr)) offset[0] = val;
                    if (caster.hasAura("displayy" + valStr)) offset[1] = val;
                    if (caster.hasAura("displayz" + valStr)) offset[2] = val;
                }
            }

            if (le instanceof Player p) {
                skin = plugin.getPlayerSkin(p.getUniqueId());
            }

            BoundingBox box = le.getBoundingBox();
            offset[1] = Math.max(offset[1], Math.min(box.getHeight() + 0.2, 4.0));

        } catch (Exception ignored) {}

        CachedAura result = new CachedAura(critical, skin, offset, now);
        auraCache.put(id, result);
        return result;
    }

    private boolean isTextDisplaySupported() {
        String v = Bukkit.getBukkitVersion();
        return v.startsWith("1.19") || v.startsWith("1.20") || v.startsWith("1.21");
    }

    private Component buildComponent(int damage, boolean critical, int skin) {
        String font = (critical ? "critical" : "normal") + skin;
        if (!font.matches("[a-z0-9_./\\-]+")) font = "normal0";
        Key key = fontKeyCache.computeIfAbsent(font, f -> Key.key("damagedisplay", f));

        Component comp = Component.empty();
        String digits = Integer.toString(damage);
        for (int i = 0; i < digits.length(); i++) {
            comp = comp.append(Component.text(digits.charAt(i)).font(key));
        }
        return comp;
    }

    private void showTextDisplay(Location loc, int damage, boolean critical, int skin, long key) {
        World world = loc.getWorld();
        if (world == null) return;

        TextDisplay display = textPool.poll();
        if (display == null || display.isDead()) {
            display = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        } else {
            display.teleport(loc);
        }

        display.setBillboard(TextDisplay.Billboard.CENTER);
        display.setShadowed(false);
        display.setSeeThrough(true);
        display.setPersistent(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.text(buildComponent(damage, critical, skin));
        display.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);

        TextDisplay finalDisplay = display;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalDisplay.remove();
            textPool.offer(finalDisplay);
            activeDisplayLocations.remove(key);
        }, 20L);
    }

    private void showArmorStand(Location loc, int damage, boolean critical, int skin, long key) {
        World world = loc.getWorld();
        if (world == null) return;

        ArmorStand stand = armorPool.poll();
        if (stand == null || stand.isDead()) {
            stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        } else {
            stand.teleport(loc);
        }

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.customName(buildComponent(damage, critical, skin));
        stand.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);

        ArmorStand finalStand = stand;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalStand.remove();
            armorPool.offer(finalStand);
            activeDisplayLocations.remove(key);
        }, 20L);
    }

    private long toKey(Location loc) {
        return ((long)(loc.getBlockX() & LOC_MASK) << 40) |
                ((long)(loc.getBlockY() & LOC_MASK) << 20) |
                (loc.getBlockZ() & LOC_MASK);
    }

    private void startTpsMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                cachedTps = Bukkit.getServer().getTPS()[0];
            } catch (Throwable ignored) {
                cachedTps = 20.0;
            }
        }, 0L, 20L);
    }

    @Override
    public void removeAll() {
        textPool.forEach(Entity::remove);
        armorPool.forEach(Entity::remove);
        textPool.clear();
        armorPool.clear();
        auraCache.clear();
        activeDisplayLocations.clear();
    }

    public record CachedAura(boolean isCritical, int skinIndex, double[] offset, long timestamp) {
        public static final CachedAura DEFAULT = new CachedAura(false, 0, new double[] {0, 2.0, 0}, System.currentTimeMillis());
    }
}
