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
    // DamageDisplay plugin 필드는 제거해도 기능에 영향이 없습니다.
    // private final DamageDisplay plugin;

    public BugReportCommand(DamageDisplay plugin) {
        // this.plugin = plugin;
        PluginCommand command = plugin.getCommand("crston");
        if (command != null) {
            command.setExecutor(this);
            // TabCompleter도 이 클래스에서 처리하므로 등록해줍니다.
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        // 1. Component 객체를 생성합니다.
        Component message = Component.text("Discord - crston")
                .color(NamedTextColor.LIGHT_PURPLE); // 2. NamedTextColor로 색상을 지정합니다.

        // 3. 생성된 Component를 전송합니다.
        sender.sendMessage(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        // Tab 자동완성 목록은 클라이언트 호환성을 위해 색상을 포함하지 않는 것이 좋습니다.
        // 따라서 이 부분은 빈 리스트를 반환하도록 수정하거나, 색상 없이 내용만 반환하는 것이 올바른 방법입니다.
        return Collections.emptyList();
    }
}