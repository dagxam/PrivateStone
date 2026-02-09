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

    private String claimId(Claim c) {
        // Читабельный ID: Nick#Number
        String nick = ownerName(c.getOwner());
        return nick + "#" + c.getNumber();
    }

    private String autoClaimName(int n, String playerName) {
        String fmt = plugin.getConfig().getString("claimAutoNameFormat", "Участок %n% %player%");
        if (fmt == null) fmt = "Участок %n% %player%";
        return fmt.replace("%n%", String.valueOf(n)).replace("%player%", playerName == null ? "Player" : playerName);
    }

    private boolean denyIfForeign(Player p, Location loc) {
        Claim at = plugin.claims().getAt(loc);
        if (at == null) return false;
        if (at.canUse(p.getUniqueId()) || canBypass(p)) return false;

        p.sendMessage(plugin.msg("insideOther").replace("%owner%", ownerName(at.getOwner())));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlockPlaced().getLocation();

        // запрещаем ставить в чужом привате
        if (denyIfForeign(p, loc)) {
            e.setCancelled(true);
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

        // если ломают любой из двух камней привата (якорь) — удаляем весь участок
        Claim anchor = plugin.claims().getByAnchor(loc);
        if (anchor != null) {
            boolean allowed = anchor.getOwner().equals(p.getUniqueId()) || canBypass(p);
            if (!allowed) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg("cantBreakAnchor"));
                return;
            }

            // удаляем участок полностью (и его имя/ID исчезает вместе с ним)
            plugin.claims().remove(anchor);
            plugin.claims().save();

            p.sendMessage(plugin.msg("removed")
                    .replace("%claimName%", anchor.getName())
                    .replace("%claimId%", claimId(anchor)));
            return;
        }

        // ломать в чужом привате нельзя
        if (denyIfForeign(p, loc)) {
            e.setCancelled(true);
        }
    }

    /**
     * Блокируем использование блоков в чужом привате:
     * двери/кнопки/рычаги/сундуки/верстаки/печки/люки/калитки/плиты и т.д.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        // Нажимные плиты (PHYSICAL)
        if (e.getAction() == Action.PHYSICAL) {
            if (e.getClickedBlock() == null) return;
            Location loc = e.getClickedBlock().getLocation();
            if (denyIfForeign(p, loc)) e.setCancelled(true);
            return;
        }

        // Клик по блоку
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        if (denyIfForeign(p, loc)) e.setCancelled(true);
    }

    /**
     * Блокируем взаимодействие с сущностями в чужом привате:
     * рамки, стойки для брони, лодки/вагонетки, жители и т.п.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getRightClicked().getLocation();
        if (denyIfForeign(p, loc)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!plugin.isProtectFromExplosions()) return;

        Iterator<org.bukkit.block.Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Location bLoc = it.next().getLocation();
            Claim c = plugin.claims().getAt(bLoc);
            if (c != null) it.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!plugin.isProtectFromExplosions()) return;

        Iterator<org.bukkit.block.Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Location bLoc = it.next().getLocation();
            Claim c = plugin.claims().getAt(bLoc);
            if (c != null) it.remove();
        }
    }
}
