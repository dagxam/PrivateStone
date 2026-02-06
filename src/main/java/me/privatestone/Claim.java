package me.privatestone;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Claim {
    private final UUID owner;
    private final String world;
    private final int x1, x2, z1, z2;

    private final int a1x, a1y, a1z;
    private final int a2x, a2y, a2z;

    private final Set<UUID> trusted = new HashSet<>();

    public Claim(UUID owner, Location a1, Location a2) {
        this.owner = owner;
        this.world = a1.getWorld().getName();

        int minX = Math.min(a1.getBlockX(), a2.getBlockX());
        int maxX = Math.max(a1.getBlockX(), a2.getBlockX());
        int minZ = Math.min(a1.getBlockZ(), a2.getBlockZ());
        int maxZ = Math.max(a1.getBlockZ(), a2.getBlockZ());

        this.x1 = minX; this.x2 = maxX;
        this.z1 = minZ; this.z2 = maxZ;

        this.a1x = a1.getBlockX(); this.a1y = a1.getBlockY(); this.a1z = a1.getBlockZ();
        this.a2x = a2.getBlockX(); this.a2y = a2.getBlockY(); this.a2z = a2.getBlockZ();
    }

    public UUID getOwner() { return owner; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getX2() { return x2; }
    public int getZ1() { return z1; }
    public int getZ2() { return z2; }

    public int getA1x() { return a1x; }
    public int getA1y() { return a1y; }
    public int getA1z() { return a1z; }
    public int getA2x() { return a2x; }
    public int getA2y() { return a2y; }
    public int getA2z() { return a2z; }

    public Set<UUID> getTrusted() { return trusted; }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public boolean isAnchor(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;

        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        boolean first = (x == a1x && y == a1y && z == a1z);
        boolean second = (x == a2x && y == a2y && z == a2z);
        return first || second;
    }

    public boolean canUse(UUID uuid) {
        if (uuid == null) return false;
        return owner.equals(uuid) || trusted.contains(uuid);
    }

    public int sizeX() { return (x2 - x1) + 1; }
    public int sizeZ() { return (z2 - z1) + 1; }

    public boolean overlaps(Claim other) {
        if (other == null) return false;
        if (!this.world.equals(other.world)) return false;

        // Axis-aligned rectangle intersection
        return this.x1 <= other.x2 && this.x2 >= other.x1
            && this.z1 <= other.z2 && this.z2 >= other.z1;
    }
}
