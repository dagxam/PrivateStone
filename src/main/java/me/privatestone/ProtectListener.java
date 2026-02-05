package me.privatestone;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectListener implements Listener {

    private final PrivateStonePlugin plugin;
    private final Map<UUID, Location> firstCorner = new HashMap<>();

    public ProtectListener(PrivateStonePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("privatestone.bypass");
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();

        if (b.getType() == Material.STONE) {
            Location loc = b.getLocation();

            Claim in = plugin.claims.getAt(loc);
            if (in != null && !in.canUse(p.getUniqueId()) && !bypass(p)) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Чужой приват");
                return;
            }

            if (!firstCorner.containsKey(p.getUniqueId())) {
                firstCorner.put(p.getUniqueId(), loc);
                p.sendMessage(ChatColor.YELLOW + "Первый угол установлен");
                return;
            }

            Location first = firstCorner.remove(p.getUniqueId());
            Claim c = new Claim(p.getUniqueId(), first, loc);

            if (!plugin.allowOverlap && plugin.claims.overlaps(c)) {
                p.sendMessage(ChatColor.RED + "Приват пересекается");
                return;
            }

            plugin.claims.add(c);
            p.sendMessage(ChatColor.GREEN + "Приват создан");
            return;
        }

        Claim c = plugin.claims.getAt(b.getLocation());
        if (c != null && !c.canUse(p.getUniqueId()) && !bypass(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Территория приватна");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (b.getType() == Material.STONE) {
            Claim c = plugin.claims.getByAnchor(b.getLocation());
            if (c != null) {
                if (!c.owner.equals(p.getUniqueId()) && !bypass(p)) {
                    e.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Не твой приват");
                    return;
                }
                plugin.claims.remove(c);
                p.sendMessage(ChatColor.GREEN + "Приват удалён");
                return;
            }
        }

        Claim c = plugin.claims.getAt(b.getLocation());
        if (c != null && !c.canUse(p.getUniqueId()) && !bypass(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Территория приватна");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();

        Claim c = plugin.claims.getAt(e.getClickedBlock().getLocation());
        if (c != null && !c.canUse(p.getUniqueId()) && !bypass(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Нельзя использовать");
        }
    }
}
