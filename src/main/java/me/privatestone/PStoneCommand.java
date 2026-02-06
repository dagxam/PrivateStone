package me.privatestone;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PStoneCommand implements CommandExecutor, TabCompleter {
    private final PrivateStonePlugin plugin;

    public PStoneCommand(PrivateStonePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAdmin(CommandSender s) {
        return s.hasPermission("privatestone.admin") || s.isOp();
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return (op != null && op.getName() != null) ? op.getName() : uuid.toString();
    }

    private boolean canBypass(Player p) {
        return p != null && p.hasPermission("privatestone.bypass");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Text.c("&aPrivateStone &7commands:"));
            sender.sendMessage(Text.c("&e/pstone give [player] [amount] &7- give claim stones"));
            sender.sendMessage(Text.c("&e/pstone trust <player> &7- trust in claim you're standing in"));
            sender.sendMessage(Text.c("&e/pstone untrust <player> &7- untrust"));
            sender.sendMessage(Text.c("&e/pstone rename <new_name> &7- rename claim you're standing in"));
            sender.sendMessage(Text.c("&e/pstone info &7- claim info where you stand"));
            sender.sendMessage(Text.c("&e/pstone reload &7- reload config"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "give" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage(plugin.msg("noPerm"));
                    return true;
                }

                Player target;
                int amount;

                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else {
                    if (sender instanceof Player p) target = p; else target = null;
                }

                if (target == null) {
                    sender.sendMessage(Text.c("&cИгрок не найден."));
                    return true;
                }

                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (Exception e) {
                        amount = plugin.getConfig().getInt("claimItem.defaultGiveAmount", 2);
                    }
                } else {
                    amount = plugin.getConfig().getInt("claimItem.defaultGiveAmount", 2);
                }

                target.getInventory().addItem(plugin.createClaimItem(amount));

                sender.sendMessage(plugin.msg("giveOk")
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%player%", target.getName()));
                return true;
            }

            case "reload" -> {
                if (!isAdmin(sender)) {
                    sender.sendMessage(plugin.msg("noPerm"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(Text.c("&aConfig reloaded."));
                return true;
            }

            case "info" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("onlyPlayers"));
                    return true;
                }

                Claim c = plugin.claims().getAt(p.getLocation());
                if (c == null) {
                    p.sendMessage(plugin.msg("notInClaim"));
                    return true;
                }

                String trusted = c.getTrusted().isEmpty()
                        ? "-"
                        : c.getTrusted().stream().map(this::nameOf).collect(Collectors.joining(", "));

                for (String line : plugin.msgList("info")) {
                    p.sendMessage(line
                            .replace("%claimName%", c.getName())
                            .replace("%owner%", nameOf(c.getOwner()))
                            .replace("%sizeX%", String.valueOf(c.sizeX()))
                            .replace("%sizeZ%", String.valueOf(c.sizeZ()))
                            .replace("%trusted%", trusted));
                }
                return true;
            }

            case "rename" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("onlyPlayers"));
                    return true;
                }

                if (args.length < 2) {
                    p.sendMessage(plugin.msg("renameUsage"));
                    return true;
                }

                Claim c = plugin.claims().getAt(p.getLocation());
                if (c == null) {
                    p.sendMessage(plugin.msg("notInClaim"));
                    return true;
                }

                if (!c.getOwner().equals(p.getUniqueId()) && !canBypass(p)) {
                    p.sendMessage(plugin.msg("notOwner"));
                    return true;
                }

                String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                if (newName.isBlank()) {
                    p.sendMessage(plugin.msg("renameEmpty"));
                    return true;
                }

                int max = Math.max(1, plugin.getConfig().getInt("claimNameMaxLength", 32));
                if (newName.length() > max) {
                    p.sendMessage(plugin.msg("renameTooLong").replace("%max%", String.valueOf(max)));
                    return true;
                }

                c.setName(newName);
                plugin.claims().save();

                p.sendMessage(plugin.msg("renameOk").replace("%claimName%", c.getName()));
                return true;
            }

            case "trust" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("onlyPlayers"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(Text.c("&cИспользование: /pstone trust <player>"));
                    return true;
                }

                Claim c = plugin.claims().getAt(p.getLocation());
                if (c == null) {
                    p.sendMessage(plugin.msg("notInClaim"));
                    return true;
                }
                if (!c.getOwner().equals(p.getUniqueId()) && !canBypass(p)) {
                    p.sendMessage(plugin.msg("notOwner"));
                    return true;
                }

                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                if (t == null || t.getUniqueId() == null) {
                    p.sendMessage(Text.c("&cИгрок не найден."));
                    return true;
                }

                c.getTrusted().add(t.getUniqueId());
                plugin.claims().save();
                p.sendMessage(plugin.msg("trustOk").replace("%player%", t.getName() != null ? t.getName() : args[1]));
                return true;
            }

            case "untrust" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg("onlyPlayers"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(Text.c("&cИспользование: /pstone untrust <player>"));
                    return true;
                }

                Claim c = plugin.claims().getAt(p.getLocation());
                if (c == null) {
                    p.sendMessage(plugin.msg("notInClaim"));
                    return true;
                }
                if (!c.getOwner().equals(p.getUniqueId()) && !canBypass(p)) {
                    p.sendMessage(plugin.msg("notOwner"));
                    return true;
                }

                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                if (t == null || t.getUniqueId() == null) {
                    p.sendMessage(Text.c("&cИгрок не найден."));
                    return true;
                }

                c.getTrusted().remove(t.getUniqueId());
                plugin.claims().save();
                p.sendMessage(plugin.msg("untrustOk").replace("%player%", t.getName() != null ? t.getName() : args[1]));
                return true;
            }

            default -> {
                sender.sendMessage(Text.c("&cНеизвестная команда. /pstone help"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help", "give", "trust", "untrust", "rename", "info", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("trust") || sub.equals("untrust")) {
                String pref = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
