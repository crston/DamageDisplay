package com.gmail.bobason01;

import com.gmail.bobason01.util.DamageDisplayRenderer;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final String type;
    private final boolean supportsTextDisplay;
    private final org.bukkit.NamespacedKey tagKey;
    private final Map<Long, Integer> heightMap = new HashMap<>();
    private final Queue<TextDisplay> textDisplayPool = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> armorStandPool = new ConcurrentLinkedQueue<>();
    private final Map<UUID, CachedAura> auraCache = new ConcurrentHashMap<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("display.type", "text_display").toLowerCase();
        this.supportsTextDisplay = isTextDisplaySupported();
        this.tagKey = new org.bukkit.NamespacedKey(plugin, "display_entity");
    }

    @Override
    public void display(Location location, int damage, boolean isCritical, int skinIndex) {
        if (getTPS() < 17.0) return;

        Location stacked = getStackedLocation(location);
        if ("text_display".equals(type) && supportsTextDisplay) {
            spawnTextDisplay(stacked, damage, isCritical, skinIndex);
        } else {
            spawnArmorStand(stacked, damage, isCritical, skinIndex);
        }
    }

    public CachedAura getAuraData(Entity damager) {
        if (!(damager instanceof LivingEntity)) return new CachedAura(false, 0);
        UUID uuid = damager.getUniqueId();
        CachedAura cached = auraCache.get(uuid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < 1000) return cached;

        boolean critical = false;
        int index = 0;

        try {
            SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(damager));
            if (caster != null) {
                critical = caster.hasAura("critical");
                for (int i = 0; i <= plugin.getMaxSkinIndex(); i++) {
                    if (caster.hasAura("damageskin" + i)) {
                        index = i;
                        break;
                    }
                }
            }
            if (damager instanceof Player p) {
                index = plugin.getPlayerSkin(p.getUniqueId());
            }
        } catch (Exception ignored) {}

        CachedAura aura = new CachedAura(critical, index, now);
        auraCache.put(uuid, aura);
        return aura;
    }

    private boolean isTextDisplaySupported() {
        String version = Bukkit.getBukkitVersion();
        return version.startsWith("1.19") || version.startsWith("1.20") || version.startsWith("1.21");
    }

    private Component buildTextComponent(int damage, boolean isCritical, int skinIndex) {
        String path = (isCritical ? "critical" : "normal") + skinIndex;

        if (path.isBlank() || !path.matches("[a-z0-9_./\\-]+")) {
            plugin.getLogger().warning("Invalid font key: " + path + ", using fallback normal0");
            path = "normal0";
        }

        Key fontKey = Key.key("damagedisplay", path);
        Component comp = Component.empty();

        for (char c : Integer.toString(damage).toCharArray()) {
            comp = comp.append(Component.text(String.valueOf(c)).font(fontKey));
        }

        return comp;
    }

    private void spawnTextDisplay(Location loc, int damage, boolean isCritical, int skinIndex) {
        World world = loc.getWorld();
        if (world == null) return;
        clearOverlappingDisplays(loc);

        TextDisplay display = textDisplayPool.poll();
        if (display == null || display.isDead()) {
            display = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        } else {
            display.teleport(loc);
        }

        display.setBillboard(TextDisplay.Billboard.CENTER);
        display.setShadowed(false);
        display.setSeeThrough(true);
        display.setPersistent(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        display.text(buildTextComponent(damage, isCritical, skinIndex));
        display.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);

        TextDisplay finalDisplay = display;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalDisplay.remove();
            textDisplayPool.offer(finalDisplay);
        }, 20L);
    }

    private void spawnArmorStand(Location loc, int damage, boolean isCritical, int skinIndex) {
        World world = loc.getWorld();
        if (world == null) return;
        clearOverlappingDisplays(loc);

        ArmorStand stand = armorStandPool.poll();
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
        stand.customName(buildTextComponent(damage, isCritical, skinIndex));
        stand.getPersistentDataContainer().set(tagKey, PersistentDataType.INTEGER, 1);

        ArmorStand finalStand = stand;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalStand.remove();
            armorStandPool.offer(finalStand);
        }, 20L);
    }

    private void clearOverlappingDisplays(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        double radius = 0.4;
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
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

    private Location getStackedLocation(Location base) {
        long key = toKey(base);
        int offset = heightMap.getOrDefault(key, 0);
        heightMap.put(key, (offset + 1) % 5);
        return base.clone().add(0, offset, 0);
    }

    private double getTPS() {
        try {
            return Bukkit.getServer().getTPS()[0];
        } catch (NoSuchMethodError | Exception e) {
            return 20.0;
        }
    }

    @Override
    public void removeAll() {
        textDisplayPool.forEach(Entity::remove);
        armorStandPool.forEach(Entity::remove);
        textDisplayPool.clear();
        armorStandPool.clear();
        auraCache.clear();
    }

    public record CachedAura(boolean isCritical, int skinIndex, long timestamp) {
        public CachedAura(boolean isCritical, int skinIndex) {
            this(isCritical, skinIndex, System.currentTimeMillis());
        }
    }
}