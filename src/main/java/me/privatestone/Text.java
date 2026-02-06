package me.privatestone;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.stream.Collectors;

public final class Text {
    private Text() {}

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static List<String> c(List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(Text::c).collect(Collectors.toList());
    }
}
