package me.privatestone;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectListener implements Listener {
    private final PrivateStonePlugin plugin;

    // player -> first corner
    private final Map<UUID, Location> firstCorner = new ConcurrentHashMap<>();

    public ProtectListener(PrivateStonePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBypass(Player p) {
        return p != null && p.hasPermission("privatestone.bypass");
    }

    private String ownerName(UUID uuid) {
        var off = plugin.getServer().getOfflinePlayer(uuid);
        return off != null && off.getName() != null ? off.getName() : uuid.toString();
    }

    private String autoClaimName(int n, String playerName) {
        String fmt = plugin.getConfig().getString("claimAutoNameFormat", "Участок %n% %player%");
        if (fmt == null) fmt = "Участок %n% %player%";
        return fmt.replace("%n%", String.valueOf(n)).replace("%player%", playerName == null ? "Player" : playerName);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlockPlaced().getLocation();

        // запрещаем ставить в чужом привате
        Claim at = plugin.claims().getAt(loc);
        if (at != null && !at.canUse(p.getUniqueId()) && !canBypass(p)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("insideOther").replace("%owner%", ownerName(at.getOwner())));
            return;
        }

        // логика создания привата — ТОЛЬКО если ставится спец-предмет
        ItemStack inHand = e.getItemInHand();
        if (e.getBlockPlaced().getType() == plugin.getClaimBlock() && plugin.isClaimItem(inHand)) {
            Location first = firstCorner.get(p.getUniqueId());
            if (first == null) {
                firstCorner.put(p.getUniqueId(), loc);
                p.sendMessage(plugin.msg("firstCorner"));
                return;
            }

            // проверка мира
            if (first.getWorld() == null || loc.getWorld() == null || !first.getWorld().equals(loc.getWorld())) {
                firstCorner.remove(p.getUniqueId());
                p.sendMessage(Text.c("&cУглы должны быть в одном мире."));
                return;
            }

            int number = plugin.claims().allocateNumber(p.getUniqueId());
            String claimName = autoClaimName(number, p.getName());

            Claim candidate = new Claim(p.getUniqueId(), first, loc, number, claimName);

            int max = plugin.getMaxSide();
            if (candidate.sizeX() > max || candidate.sizeZ() > max) {
                firstCorner.remove(p.getUniqueId());
                p.sendMessage(plugin.msg("tooLarge").replace("%max%", String.valueOf(max)));
                return;
            }

            if (!plugin.isAllowOverlap() && plugin.claims().overlaps(candidate)) {
                firstCorner.remove(p.getUniqueId());
                p.sendMessage(plugin.msg("overlap"));
                return;
            }

            plugin.claims().add(candidate);
            plugin.claims().save();
            firstCorner.remove(p.getUniqueId());

            p.sendMessage(plugin.msg("created")
                    .replace("%claimName%", candidate.getName())
                    .replace("%sizeX%", String.valueOf(candidate.sizeX()))
                    .replace("%sizeZ%", String.valueOf(candidate.sizeZ()))
                    .replace("%owner%", ownerName(candidate.getOwner())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlock().getLocation();

        // если ломают якорь привата
        Claim anchor = plugin.claims().getByAnchor(loc);
        if (anchor != null) {
            boolean allowed = anchor.getOwner().equals(p.getUniqueId()) || canBypass(p);
            if (!allowed) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg("cantBreakAnchor"));
                return;
            }
            plugin.claims().remove(anchor);
            plugin.claims().save();
            p.sendMessage(plugin.msg("removed"));
            return;
        }

        // ломать в чужом привате нельзя
        Claim at = plugin.claims().getAt(loc);
        if (at != null && !at.canUse(p.getUniqueId()) && !canBypass(p)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("insideOther").replace("%owner%", ownerName(at.getOwner())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Location loc = e.getClickedBlock().getLocation();

        Claim at = plugin.claims().getAt(loc);
        if (at != null && !at.canUse(p.getUniqueId()) && !canBypass(p)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("insideOther").replace("%owner%", ownerName(at.getOwner())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!plugin.isProtectFromExplosions()) return;

        Iterator<org.bukkit.block.Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Location loc = it.next().getLocation();
            Claim c = plugin.claims().getAt(loc);
            if (c != null) it.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!plugin.isProtectFromExplosions()) return;

        Iterator<org.bukkit.block.Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Location loc = it.next().getLocation();
            Claim c = plugin.claims().getAt(loc);
            if (c != null) it.remove();
        }
    }
}
