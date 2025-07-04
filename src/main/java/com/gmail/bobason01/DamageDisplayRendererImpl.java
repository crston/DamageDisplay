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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final String type;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Map<Long, Integer> heightOffsets = new HashMap<>();
    private final Queue<TextDisplay> textPool = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> armorPool = new ConcurrentLinkedQueue<>();
    private final Map<UUID, CachedAura> auraCache = new ConcurrentHashMap<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("display.type", "text_display").toLowerCase();
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
    }

    @Override
    public void display(Location loc, int damage, boolean isCritical, int skinIndex) {
        if (getTPS() < 17.0 || damage <= 0) return;

        Location stacked = getOffsetLocation(loc);
        if ("text_display".equals(type) && useTextDisplay) {
            showTextDisplay(stacked, damage, isCritical, skinIndex);
        } else {
            showArmorStand(stacked, damage, isCritical, skinIndex);
        }
    }

    public CachedAura getAuraData(Entity damager) {
        if (!(damager instanceof LivingEntity le)) return new CachedAura(false, 0);
        UUID id = le.getUniqueId();
        CachedAura cached = auraCache.get(id);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < 1000) return cached;

        boolean critical = false;
        int skin = 0;

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
            }
            if (le instanceof Player p) skin = plugin.getPlayerSkin(p.getUniqueId());
        } catch (Exception ignored) {}

        CachedAura aura = new CachedAura(critical, skin, now);
        auraCache.put(id, aura);
        return aura;
    }

    private boolean isTextDisplaySupported() {
        String v = Bukkit.getBukkitVersion();
        return v.startsWith("1.19") || v.startsWith("1.20") || v.startsWith("1.21");
    }

    private Component buildComponent(int damage, boolean critical, int skin) {
        String font = (critical ? "critical" : "normal") + skin;
        if (!font.matches("[a-z0-9_./\\-]+")) font = "normal0";
        Key key = Key.key("damagedisplay", font);

        Component comp = Component.empty();
        for (char c : Integer.toString(damage).toCharArray()) {
            comp = comp.append(Component.text(String.valueOf(c)).font(key));
        }
        return comp;
    }

    private void showTextDisplay(Location loc, int damage, boolean critical, int skin) {
        World world = loc.getWorld();
        if (world == null) return;
        removeNearbyDisplays(loc);

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
        }, 20L);
    }

    private void showArmorStand(Location loc, int damage, boolean critical, int skin) {
        World world = loc.getWorld();
        if (world == null) return;
        removeNearbyDisplays(loc);

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
        }, 20L);
    }

    private void removeNearbyDisplays(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        double r = 0.4;
        for (Entity e : world.getNearbyEntities(loc, r, r, r)) {
            if ((e instanceof TextDisplay || e instanceof ArmorStand) && !e.isDead() &&
                    e.getPersistentDataContainer().has(tagKey, PersistentDataType.INTEGER)) {
                e.remove();
            }
        }
    }

    private long toKey(Location loc) {
        return ((long) loc.getBlockX() & 0xFFFFFL) << 40 |
                ((long) loc.getBlockY() & 0xFFFFFL) << 20 |
                ((long) loc.getBlockZ() & 0xFFFFFL);
    }

    private Location getOffsetLocation(Location base) {
        long key = toKey(base);
        int offset = heightOffsets.getOrDefault(key, 0);
        heightOffsets.put(key, (offset + 1) % 5);
        return base.clone().add(0, offset, 0);
    }

    private double getTPS() {
        try {
            return Bukkit.getServer().getTPS()[0];
        } catch (Throwable ignored) {
            return 20.0;
        }
    }

    @Override
    public void removeAll() {
        textPool.forEach(Entity::remove);
        armorPool.forEach(Entity::remove);
        textPool.clear();
        armorPool.clear();
        auraCache.clear();
    }

    public record CachedAura(boolean isCritical, int skinIndex, long timestamp) {
        public CachedAura(boolean isCritical, int skinIndex) {
            this(isCritical, skinIndex, System.currentTimeMillis());
        }
    }
}
