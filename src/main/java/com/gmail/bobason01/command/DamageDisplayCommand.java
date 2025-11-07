package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.blacklist.BlacklistManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * DamageDisplayCommand
 * - Java 21 기반 극한 성능 최적화 버전
 * - 불필요한 객체 생성 최소화 및 분기 효율화
 */
public final class DamageDisplayCommand implements TabExecutor {

    private static final String PERM_SET_OTHERS = "damagedisplay.set.others";
    private static final String PERM_BLACKLIST = "damagedisplay.blacklist";
    private static final String PREFIX = "[DamageDisplay] ";

    private static final List<String> SUBCOMMANDS = List.of("reload", "set", "blacklist");
    private static final List<String> BLACKLIST_SUBS = List.of("add", "remove", "list");
    private static final List<String> SKIN_INDICES = List.of("0", "1", "2", "3");
    private static final List<String> EMPTY_LIST = Collections.emptyList();

    // entityName → EntityType 캐시
    private static final Map<String, EntityType> ENTITY_NAME_TO_TYPE = new HashMap<>();
    static {
        for (EntityType type : EntityType.values()) {
            ENTITY_NAME_TO_TYPE.put(type.name().toLowerCase(Locale.ENGLISH), type);
        }
    }

    private final DamageDisplay plugin;
    private final BlacklistManager blacklistManager;

    public DamageDisplayCommand(@NotNull DamageDisplay plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.blacklistManager = plugin.getBlacklistManager();

        PluginCommand cmd = plugin.getCommand("damagedisplay");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("[DamageDisplayCommand] Command 'damagedisplay' not found in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /damagedisplay <reload|set|blacklist>");
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ENGLISH);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "blacklist" -> handleBlacklist(sender, args);
            default -> {
                sender.sendMessage("Unknown subcommand. Use: reload, set, blacklist");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(PREFIX + "Reloaded.");
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
            sender.sendMessage("Invalid index: must be a number");
            return true;
        }

        final Player target;
        if (args.length == 3) {
            if (!sender.hasPermission(PERM_SET_OTHERS)) {
                sender.sendMessage("You do not have permission to change other players' skins.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Please specify a player name when using this command from the console.");
            return true;
        }

        if (index > 0 && !target.hasPermission("damagedisplay.skin." + index)) {
            sender.sendMessage(target.getName() + " does not have permission for skin " + index + ".");
            return true;
        }

        plugin.saveSkin(target.getUniqueId(), index);
        sender.sendMessage("Set damage skin of " + target.getName() + " to " + index);
        return true;
    }

    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_BLACKLIST)) {
            sender.sendMessage("You lack permission for blacklist management.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ENGLISH);
        switch (action) {
            case "list" -> {
                var blacklisted = blacklistManager.getBlacklisted();
                if (blacklisted.isEmpty()) {
                    sender.sendMessage("Blacklisted Entities: none");
                    return true;
                }
                StringJoiner joiner = new StringJoiner(", ", "Blacklisted Entities: ", "");
                for (EntityType type : blacklisted) joiner.add(type.name());
                sender.sendMessage(joiner.toString());
                return true;
            }
            case "add", "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("Specify entity type for add/remove.");
                    return true;
                }

                EntityType type = ENTITY_NAME_TO_TYPE.get(args[2].toLowerCase(Locale.ENGLISH));
                if (type == null) {
                    sender.sendMessage("Invalid entity type: " + args[2]);
                    return true;
                }

                if (action.equals("add")) {
                    blacklistManager.addToBlacklist(type);
                    sender.sendMessage("Added to blacklist: " + type.name());
                } else {
                    blacklistManager.removeFromBlacklist(type);
                    sender.sendMessage("Removed from blacklist: " + type.name());
                }
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return SUBCOMMANDS;

        if ("set".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return SKIN_INDICES;
            if (args.length == 3) return null; // Bukkit will handle player names
        }

        if ("blacklist".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return BLACKLIST_SUBS;
            if (args.length == 3 && ("add".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
                return List.copyOf(ENTITY_NAME_TO_TYPE.keySet());
            }
        }

        return EMPTY_LIST;
    }
}
