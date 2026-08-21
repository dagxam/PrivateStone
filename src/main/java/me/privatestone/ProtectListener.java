package me.privatestone;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectListener implements Listener {
    private final PrivateStonePlugin plugin;
    private final Map<UUID, Location> firstCorner = new ConcurrentHashMap<>();

    public ProtectListener(PrivateStonePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBypass(Player player) {
        return player != null && player.hasPermission("privatestone.bypass");
    }

    private String ownerName(UUID uuid) {
        var off = plugin.getServer().getOfflinePlayer(uuid);
        return off != null && off.getName() != null ? off.getName() : uuid.toString();
    }

    private String claimId(Claim claim) {
        return ownerName(claim.getOwner()) + "#" + claim.getNumber();
    }

    private String autoClaimName(int number, String playerName) {
        String format = plugin.getConfig().getString("claimAutoNameFormat", "Участок %n% %player%");
        if (format == null) format = "Участок %n% %player%";
        return format.replace("%n%", String.valueOf(number))
                .replace("%player%", playerName == null ? "Player" : playerName);
    }

    private boolean denyIfForeign(Player player, Location location) {
        Claim claim = plugin.claims().getAt(location);
        if (claim == null || claim.canUse(player.getUniqueId()) || canBypass(player)) return false;
        player.sendMessage(plugin.msg("insideOther").replace("%owner%", ownerName(claim.getOwner())));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();

        if (denyIfForeign(player, location)) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItemInHand();
        if (!plugin.isClaimItem(item)) return;

        Location first = firstCorner.get(player.getUniqueId());
        if (first == null) {
            firstCorner.put(player.getUniqueId(), location);
            player.sendMessage(plugin.msg("firstCorner"));
            return;
        }

        if (first.getWorld() == null || location.getWorld() == null || !first.getWorld().equals(location.getWorld())) {
            firstCorner.remove(player.getUniqueId());
            player.sendMessage(Text.c("&cУглы должны быть в одном мире."));
            return;
        }

        int number = plugin.claims().allocateNumber(player.getUniqueId());
        Claim candidate = new Claim(player.getUniqueId(), first, location, number, autoClaimName(number, player.getName()));

        int max = plugin.getMaxSide();
        if (candidate.sizeX() > max || candidate.sizeZ() > max) {
            firstCorner.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("tooLarge").replace("%max%", String.valueOf(max)));
            return;
        }

        if (!plugin.isAllowOverlap() && plugin.claims().overlaps(candidate)) {
            firstCorner.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("overlap"));
            return;
        }

        plugin.claims().add(candidate);
        plugin.claims().save();
        firstCorner.remove(player.getUniqueId());

        player.sendMessage(plugin.msg("created")
                .replace("%claimName%", candidate.getName())
                .replace("%sizeX%", String.valueOf(candidate.sizeX()))
                .replace("%sizeZ%", String.valueOf(candidate.sizeZ()))
                .replace("%owner%", ownerName(candidate.getOwner())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        Claim anchor = plugin.claims().getByAnchor(location);
        if (anchor != null) {
            boolean allowed = anchor.getOwner().equals(player.getUniqueId()) || canBypass(player);
            if (!allowed) {
                event.setCancelled(true);
                player.sendMessage(plugin.msg("cantBreakAnchor"));
                return;
            }
            plugin.claims().remove(anchor);
            plugin.claims().save();
            player.sendMessage(plugin.msg("removed")
                    .replace("%claimName%", anchor.getName())
                    .replace("%claimId%", claimId(anchor)));
            return;
        }

        if (denyIfForeign(player, location)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (denyIfForeign(event.getPlayer(), event.getClickedBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (denyIfForeign(event.getPlayer(), event.getRightClicked().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.isProtectFromExplosions()) return;
        Iterator<org.bukkit.block.Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (plugin.claims().getAt(iterator.next().getLocation()) != null) iterator.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.isProtectFromExplosions()) return;
        Iterator<org.bukkit.block.Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            if (plugin.claims().getAt(iterator.next().getLocation()) != null) iterator.remove();
        }
    }
}
