package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BugReportCommand implements TabExecutor {
    private final DamageDisplay plugin;

    public BugReportCommand(DamageDisplay plugin) {
        this.plugin = plugin;
        PluginCommand command = plugin.getCommand("crston");
        if (command != null) command.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Discord - crston");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of(ChatColor.LIGHT_PURPLE + "Discord - crston");
        }
        return List.of();
    }
}
