package me.privatestone;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Claim {

    public final UUID owner;
    public final String world;

    public final int x1, z1, x2, z2;
    public final int a1x, a1y, a1z;
    public final int a2x, a2y, a2z;

    public final Set<UUID> trusted = new HashSet<>();

    public Claim(UUID owner, Location l1, Location l2) {
        this.owner = owner;
        this.world = l1.getWorld().getName();

        a1x = l1.getBlockX(); a1y = l1.getBlockY(); a1z = l1.getBlockZ();
        a2x = l2.getBlockX(); a2y = l2.getBlockY(); a2z = l2.getBlockZ();

        x1 = Math.min(a1x, a2x);
        x2 = Math.max(a1x, a2x);
        z1 = Math.min(a1z, a2z);
        z2 = Math.max(a1z, a2z);
    }

    public boolean contains(Location l) {
        if (!l.getWorld().getName().equals(world)) return false;
        int x = l.getBlockX();
        int z = l.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public boolean isAnchor(Location l) {
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return (x==a1x && y==a1y && z==a1z) || (x==a2x && y==a2y && z==a2z);
    }

    public boolean canUse(UUID u) {
        return owner.equals(u) || trusted.contains(u);
    }

    public String key() {
        return world + ":" + a1x + ":" + a1y + ":" + a1z + "|" +
               a2x + ":" + a2y + ":" + a2z;
    }
}
