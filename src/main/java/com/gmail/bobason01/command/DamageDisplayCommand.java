package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.blacklist.BlacklistManager;
import com.gmail.bobason01.util.ResourcePackBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DamageDisplayCommand implements TabExecutor {

    private static final String PERM_SET_OTHERS = "damagedisplay.set.others";
    private static final String PERM_BLACKLIST = "damagedisplay.blacklist";
    private static final String PERM_RESOURCE = "damagedisplay.resource";
    private static final List<String> ROOT_SUBS = List.of("reload", "set", "blacklist", "resourcebuild");
    private static final List<String> BLACKLIST_SUBS = List.of("add", "remove", "list");

    private final DamageDisplay plugin;
    private final BlacklistManager blacklistManager;
    private final Map<String, EntityType> entityNameCache = new TreeMap<>();

    public DamageDisplayCommand(DamageDisplay plugin) {
        this.plugin = plugin;
        this.blacklistManager = plugin.getBlacklistManager();

        for (EntityType type : EntityType.values()) {
            entityNameCache.put(type.name().toLowerCase(Locale.ROOT), type);
        }

        PluginCommand cmd = plugin.getCommand("damagedisplay");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "blacklist" -> handleBlacklist(sender, args);
            case "resourcebuild" -> handleResourceBuild(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("/damagedisplay reload");
        sender.sendMessage("/damagedisplay set <skinIndex> [player]");
        sender.sendMessage("/damagedisplay blacklist <add|remove|list> [entityType]");
        sender.sendMessage("/damagedisplay resourcebuild");
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage("DamageDisplay reloaded");
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /damagedisplay set <skinIndex> [player]");
            return true;
        }

        final int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Skin index must be a number");
            return true;
        }

        if (index < 0 || index > plugin.getMaxSkinIndex()) {
            sender.sendMessage("Skin index must be between 0 and " + plugin.getMaxSkinIndex());
            return true;
        }

        final Player target;
        if (args.length == 3) {
            if (!sender.hasPermission(PERM_SET_OTHERS)) {
                sender.sendMessage("No permission to change other players' skins");
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found");
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Must specify player when using from console");
                return true;
            }
            target = p;
        }

        if (index > 0 && !target.hasPermission("damagedisplay.skin." + index)) {
            sender.sendMessage("Player does not have permission for skin " + index);
            return true;
        }

        plugin.saveSkin(target.getUniqueId(), index);
        sender.sendMessage("Set damage skin of " + target.getName() + " to " + index);
        return true;
    }

    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_BLACKLIST)) {
            sender.sendMessage("No permission for blacklist");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list" -> {
                var set = blacklistManager.getBlacklisted();
                if (set.isEmpty()) {
                    sender.sendMessage("Blacklist is empty");
                } else {
                    String joined = String.join(", ", set.stream().map(Enum::name).sorted().toList());
                    sender.sendMessage("Blacklisted: " + joined);
                }
                yield true;
            }
            case "add", "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("Specify entity type");
                    yield true;
                }
                EntityType type = entityNameCache.get(args[2].toLowerCase(Locale.ROOT));
                if (type == null) {
                    sender.sendMessage("Invalid entity type");
                    yield true;
                }
                if (action.equals("add")) {
                    if (blacklistManager.addToBlacklist(type)) {
                        sender.sendMessage("Added " + type.name() + " to blacklist");
                    } else {
                        sender.sendMessage("Already blacklisted");
                    }
                } else {
                    if (blacklistManager.removeFromBlacklist(type)) {
                        sender.sendMessage("Removed " + type.name() + " from blacklist");
                    } else {
                        sender.sendMessage("Not in blacklist");
                    }
                }
                yield true;
            }
            default -> {
                sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
                yield true;
            }
        };
    }

    private boolean handleResourceBuild(CommandSender sender) {
        if (!sender.hasPermission(PERM_RESOURCE)) {
            sender.sendMessage("No permission for resourcebuild");
            return true;
        }
        ResourcePackBuilder builder = plugin.getResourcePackBuilder();
        builder.buildAsync();
        sender.sendMessage("Started resource pack build");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return ROOT_SUBS;
        if ("set".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                int max = plugin.getMaxSkinIndex();
                List<String> out = new ArrayList<>(max + 1);
                for (int i = 0; i <= max; i++) out.add(Integer.toString(i));
                return out;
            }
            return null;
        }
        if ("blacklist".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return BLACKLIST_SUBS;
            if (args.length == 3 && ("add".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
                return new ArrayList<>(entityNameCache.keySet());
            }
        }
        return Collections.emptyList();
    }
}