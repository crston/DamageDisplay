package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import com.gmail.bobason01.blacklist.BlacklistManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class DamageDisplayCommand implements TabExecutor {
    private final DamageDisplay plugin;
    private final BlacklistManager blacklistManager;

    public DamageDisplayCommand(DamageDisplay plugin) {
        this.plugin = plugin;
        this.blacklistManager = plugin.getBlacklistManager();
        PluginCommand command = plugin.getCommand("damagedisplay");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /damagedisplay <reload|set|blacklist>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("[DamageDisplay] Reloaded.");
                return true;
            }
            case "set" -> {
                return handleSet(sender, args);
            }
            case "blacklist" -> {
                return handleBlacklist(sender, args);
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Use: reload, set, blacklist");
                return true;
            }
        }
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /damagedisplay set <skinIndex> [player]");
            return true;
        }

        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid index: must be a number");
            return true;
        }

        Player target = null;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage("Only players can use this command without specifying a player name.");
            return true;
        }

        if (index > 0 && !target.hasPermission("damagedisplay.skin." + index)) {
            sender.sendMessage("You do not have permission for this skin.");
            return true;
        }

        plugin.saveSkin(target.getUniqueId(), index);
        sender.sendMessage("Set damage skin of " + target.getName() + " to " + index);
        return true;
    }

    private boolean handleBlacklist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("damagedisplay.blacklist")) {
            sender.sendMessage("You lack permission for blacklist management.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
            return true;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("list")) {
            String result = blacklistManager.getBlacklisted().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            sender.sendMessage("Blacklisted Entities: " + (result.isEmpty() ? "none" : result));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("Specify entity type for add/remove.");
            return true;
        }

        try {
            EntityType type = EntityType.valueOf(args[2].toUpperCase());
            if (sub.equals("add")) {
                blacklistManager.addToBlacklist(type);
                sender.sendMessage("Added to blacklist: " + type.name());
            } else if (sub.equals("remove")) {
                blacklistManager.removeFromBlacklist(type);
                sender.sendMessage("Removed from blacklist: " + type.name());
            } else {
                sender.sendMessage("Usage: /damagedisplay blacklist <add|remove|list> [entityType]");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid entity type: " + args[2]);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "set", "blacklist");
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length == 2) return List.of("0", "1", "2", "3");
            if (args.length == 3) return null; // Let Bukkit suggest player names
        }

        if (args[0].equalsIgnoreCase("blacklist")) {
            if (args.length == 2) return Arrays.asList("add", "remove", "list");
            if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                return Arrays.stream(EntityType.values())
                        .map(Enum::name)
                        .map(String::toLowerCase)
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}