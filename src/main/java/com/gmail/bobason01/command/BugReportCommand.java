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

/**
 * BugReportCommand
 * - /crston 명령어 처리
 * - GC-friendly, 불변 구조, Java 21의 record 문법 기반
 */
public record BugReportCommand(@NotNull DamageDisplay plugin) implements TabExecutor {

    // 미리 생성된 불변 Adventure Component (빌더 경로 제거)
    private static final Component MESSAGE = Component.textOfChildren(
            Component.text("Discord - crston", NamedTextColor.LIGHT_PURPLE)
    );

    // 빈 리스트 캐싱
    private static final List<String> EMPTY_LIST = List.of();

    public BugReportCommand {
        Objects.requireNonNull(plugin, "plugin must not be null");
        final PluginCommand cmd = plugin.getCommand("crston");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        } else {
            plugin.getLogger().warning(() ->
                    String.format("[BugReportCommand] Command '%s' not found in plugin.yml", "crston"));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        sender.sendMessage(MESSAGE);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        return EMPTY_LIST;
    }
}
