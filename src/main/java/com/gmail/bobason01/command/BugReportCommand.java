package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public record BugReportCommand(@NotNull DamageDisplay plugin) implements TabExecutor {

    private static final Component MESSAGE = Component.textOfChildren(
            Component.text("Discord - crston", NamedTextColor.LIGHT_PURPLE)
    );
    private static final List<String> EMPTY_LIST = List.of();

    public BugReportCommand {
        Objects.requireNonNull(plugin, "plugin must not be null");
        final PluginCommand cmd = plugin.getCommand("crston");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        sender.sendMessage(MESSAGE);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return EMPTY_LIST;
    }
}