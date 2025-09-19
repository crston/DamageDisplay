package com.gmail.bobason01.command;

import com.gmail.bobason01.DamageDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class BugReportCommand implements TabExecutor {

    public BugReportCommand(DamageDisplay plugin) {
        PluginCommand command = plugin.getCommand("crston");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        Component message = Component.text("Discord - crston")
                .color(NamedTextColor.LIGHT_PURPLE); // 2. NamedTextColor로 색상을 지정합니다.
        sender.sendMessage(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        return Collections.emptyList();
    }
}