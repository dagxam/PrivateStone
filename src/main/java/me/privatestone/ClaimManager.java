package me.privatestone;

import org.bukkit.Location;

import java.util.*;

public class ClaimManager {

    private final Map<String, Claim> claims = new HashMap<>();

    public void add(Claim c) {
        claims.put(c.key(), c);
    }

    public void remove(Claim c) {
        claims.remove(c.key());
    }

    public Claim getByAnchor(Location l) {
        for (Claim c : claims.values())
            if (c.isAnchor(l)) return c;
        return null;
    }

    public Claim getAt(Location l) {
        for (Claim c : claims.values())
            if (c.contains(l)) return c;
        return null;
    }

    public boolean overlaps(Claim n) {
        for (Claim c : claims.values()) {
            if (!c.world.equals(n.world)) continue;
            boolean ox = n.x1 <= c.x2 && n.x2 >= c.x1;
            boolean oz = n.z1 <= c.z2 && n.z2 >= c.z1;
            if (ox && oz) return true;
        }
        return false;
    }
}
