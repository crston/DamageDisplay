package com.gmail.bobason01;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.craftbukkit.v1_21_R3.entity.CraftTextDisplay;
import net.minecraft.network.chat.IChatBaseComponent;
import org.intellij.lang.annotations.Subst;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.network.syncher.DataWatcherObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

public class EntityDamageListener implements Listener {
    private final DamageDisplay plugin;
    private final Set<Entity> activeTextDisplays = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<Location, List<TextDisplay>> locationTextDisplays = new ConcurrentHashMap<>();

    public EntityDamageListener(DamageDisplay plugin) {
        this.plugin = plugin;
        BukkitAudiences adventure = BukkitAudiences.create(plugin);
        PlainTextComponentSerializer plainTextSerializer = PlainTextComponentSerializer.plainText();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();

        // Check if the entity type is blacklisted
        if (plugin.isEntityBlacklisted(entity.getType())) {
            return; // Skip showing damage for blacklisted entity types
        }

        double damage = BigDecimal.valueOf(event.getFinalDamage()).setScale(0, RoundingMode.HALF_UP).doubleValue();
        Location loc = entity.getLocation().add(0, 2, 0);

        boolean hasCriticalAura = false;

        try {
            SkillCaster caster = MythicProvider.get().getSkillManager().getCaster(BukkitAdapter.adapt(entity));
            if (caster != null) {
                hasCriticalAura = caster.hasAura("critical");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "크리티컬 오라 확인 중 오류 발생: {0}", e.getMessage());
        }

        int damageSkinNumber = plugin.getEntitySkin(entity);
        String damageString = String.valueOf((int) damage);
        createComponentDisplay(loc, damageString, hasCriticalAura, damageSkinNumber);
    }

    private void createComponentDisplay(Location location, @Subst("") String damageString, @Subst("") boolean hasCriticalAura, @Subst("") int damageSkinNumber) {
        @Subst("") String skinPrefix = hasCriticalAura ? "critical" : "normal";
        int maxSkinNumber = 3; // Assuming you have skins 0, 1, and 2

        // Ensure damageSkinNumber is within valid range
        if (damageSkinNumber < 0 || damageSkinNumber >= maxSkinNumber) {
            plugin.getLogger().warning("Invalid damageSkinNumber: " + damageSkinNumber + ". Using default skin 0.");
            damageSkinNumber = 0; // Use a default skin
        }

        Component finalComponent = Component.empty();
        for (int i = 0; i < damageString.length(); i++) {
            char digit = damageString.charAt(i);
            String font = "damagedisplay:" + skinPrefix + damageSkinNumber;
            Component digitComponent = Component.text(String.valueOf(digit)).font(net.kyori.adventure.key.Key.key(font));
            finalComponent = finalComponent.append(digitComponent);
        }

        try {
            World world = Objects.requireNonNull(location.getWorld());

            // Remove existing text displays at the location
            List<TextDisplay> displaysAtLocation = locationTextDisplays.get(location);
            if (displaysAtLocation != null) {
                synchronized (displaysAtLocation) {
                    for (TextDisplay existingDisplay : displaysAtLocation) {
                        if (existingDisplay != null && !existingDisplay.isDead()) {
                            existingDisplay.remove();
                        }
                    }
                    displaysAtLocation.clear();
                }
            } else {
                displaysAtLocation = new ArrayList<>();
                locationTextDisplays.put(location, displaysAtLocation);
            }


            TextDisplay textDisplay = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
            CraftTextDisplay craftTextDisplay = (CraftTextDisplay) textDisplay;

            // Get the NMS Entity
            Object entityText = craftTextDisplay.getHandle();

            // Get the NMS DataWatcher (SynchedEntityData)
            Method getEntityData = entityText.getClass().getMethod("getEntityData");
            Object dataWatcher = getEntityData.invoke(entityText);

            // Get the NMS DATA_BACKGROUND_COLOR_ID field
            Class<?> displayClass = Class.forName("net.minecraft.world.entity.Display$TextDisplay");
            Field DATA_BACKGROUND_COLOR_ID_field = displayClass.getDeclaredField("DATA_BACKGROUND_COLOR_ID");
            DATA_BACKGROUND_COLOR_ID_field.setAccessible(true);
            Object DATA_BACKGROUND_COLOR_ID = DATA_BACKGROUND_COLOR_ID_field.get(null);

            // Cast to DataWatcherObject (the correct type)
            DataWatcherObject<?> dataWatcherObject = (DataWatcherObject<?>) DATA_BACKGROUND_COLOR_ID;

            // Set the background color to transparent (0)
            Method set = dataWatcher.getClass().getMethod("set", DataWatcherObject.class, Object.class);
            set.invoke(dataWatcher, dataWatcherObject, 0);

            // Mark the DataWatcher as dirty (important for changes to be sent)
            Method markDirty = dataWatcher.getClass().getMethod("a", DataWatcherObject.class);
            markDirty.invoke(dataWatcher, dataWatcherObject);

            // Convert Adventure Component to NMS IChatBaseComponent
            IChatBaseComponent iChatBaseComponent = CraftChatMessage.fromJSON(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(finalComponent));

            // Set the text of the TextDisplay using NMS
            Method setCustomNameMethod = entityText.getClass().getMethod("a", IChatBaseComponent.class);
            setCustomNameMethod.invoke(entityText, iChatBaseComponent);

            textDisplay.setBillboard(TextDisplay.Billboard.VERTICAL);
            textDisplay.setShadowed(false);
            textDisplay.setSeeThrough(true);

            final List<TextDisplay> displaysAtLocationFinal = displaysAtLocation; // Make it effectively final
            synchronized (displaysAtLocationFinal) {
                displaysAtLocationFinal.add(textDisplay);
            }

            new BukkitRunnable() {
                double t = 0;

                @Override
                public void run() {
                    if (t >= 30) {
                        textDisplay.remove();
                        synchronized (displaysAtLocationFinal) {
                            displaysAtLocationFinal.remove(textDisplay);
                        }
                        cancel();
                        return;
                    }
                    t += 1;
                    textDisplay.teleport(textDisplay.getLocation().add(0, 0.05, 0));
                }
            }.runTaskTimer(plugin, 1, 1);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating and displaying damage: " + e.getMessage(), e);
        }
    }

    public void removeAllText() {
        synchronized (activeTextDisplays) {
            for (Entity textDisplay : activeTextDisplays) {
                textDisplay.remove();
            }
            activeTextDisplays.clear();
        }

        // Clear all location-based text displays
        locationTextDisplays.values().forEach(displays -> {
            synchronized (displays) {
                displays.forEach(Entity::remove);
                displays.clear();
            }
        });
        locationTextDisplays.clear();
    }
}