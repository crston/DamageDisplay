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
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.*;

public class DamageDisplayRendererImpl implements DamageDisplayRenderer {
    private final DamageDisplay plugin;
    private final String type;
    private final boolean useTextDisplay;
    private final NamespacedKey tagKey;
    private final Queue<TextDisplay> textPool = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> armorPool = new ConcurrentLinkedQueue<>();
    private final Map<UUID, CachedAura> auraCache = new ConcurrentHashMap<>();
    private final Map<String, Key> fontKeyCache = new ConcurrentHashMap<>();

    public DamageDisplayRendererImpl(DamageDisplay plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("display.type", "text_display").toLowerCase();
        this.useTextDisplay = isTextDisplaySupported();
        this.tagKey = new NamespacedKey(plugin, "display_entity");
    }

    @Override
    public void display(Location loc, int damage, boolean isCritical, int skinIndex, double[] offset) {
        if (damage <= 0) return;

        Location displayLoc = loc.clone().add(offset[0], offset[1], offset[2]);

        if (useTextDisplay) {
            showTextDisplay(displayLoc, damage, isCritical, skinIndex);
        } else {
            showArmorStand(displayLoc, damage, isCritical, skinIndex);
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

    private void showTextDisplay(Location loc, int damage, boolean critical, int skin) {
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

        animateDisplay(display);
    }

    private void animateDisplay(TextDisplay display) {
        Location origin = display.getLocation().clone();
        double gravity = 0.035;
        double velocity = 0.17;

        new BukkitRunnable() {
            double t = 0;
            double y = 0;
            double vy = velocity;

            @Override
            public void run() {
                y += vy;
                vy -= gravity;
                t++;

                Location newLoc = origin.clone().add(0, y, 0);
                display.teleport(newLoc);

                if (vy < -0.15 || t > 15) { // 충분히 떨어지면 바로 제거
                    display.remove();
                    textPool.offer(display);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void showArmorStand(Location loc, int damage, boolean critical, int skin) {
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
        }, 20L);
    }

    @Override
    public void removeAll() {
        textPool.forEach(Entity::remove);
        armorPool.forEach(Entity::remove);
        textPool.clear();
        armorPool.clear();
        auraCache.clear();
    }

    public record CachedAura(boolean isCritical, int skinIndex, double[] offset, long timestamp) {
        public static final CachedAura DEFAULT = new CachedAura(false, 0, new double[]{0, 2.0, 0}, System.currentTimeMillis());
    }
}