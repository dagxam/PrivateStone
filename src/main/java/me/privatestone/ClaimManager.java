package me.privatestone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ClaimManager {
    private final PrivateStonePlugin plugin;

    // claimId -> claim
    private final Map<String, Claim> claims = new HashMap<>();

    public ClaimManager(PrivateStonePlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<Claim> getAll() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public List<Claim> getByOwner(UUID owner) {
        return claims.values().stream().filter(c -> c.getOwner().equals(owner)).collect(Collectors.toList());
    }

    public void add(Claim claim) {
        claims.put(makeId(claim), claim);
    }

    public void remove(Claim claim) {
        claims.remove(makeId(claim));
    }

    public Claim getAt(Location loc) {
        for (Claim c : claims.values()) {
            if (c.contains(loc)) return c;
        }
        return null;
    }

    public Claim getByAnchor(Location loc) {
        for (Claim c : claims.values()) {
            if (c.isAnchor(loc)) return c;
        }
        return null;
    }

    public boolean overlaps(Claim candidate) {
        for (Claim c : claims.values()) {
            if (c.overlaps(candidate)) return true;
        }
        return false;
    }

    private String makeId(Claim c) {
        // stable id based on world + both anchors
        return c.getWorld() + ":" +
                c.getA1x() + "," + c.getA1y() + "," + c.getA1z() + "|" +
                c.getA2x() + "," + c.getA2y() + "," + c.getA2z();
    }

    public void load() {
        claims.clear();
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var list = yml.getMapList("claims");
        for (var map : list) {
            try {
                UUID owner = UUID.fromString((String) map.get("owner"));
                String world = (String) map.get("world");

                int a1x = (int) map.get("a1x");
                int a1y = (int) map.get("a1y");
                int a1z = (int) map.get("a1z");

                int a2x = (int) map.get("a2x");
                int a2y = (int) map.get("a2y");
                int a2z = (int) map.get("a2z");

                var bw = Bukkit.getWorld(world);
                if (bw == null) continue;

                Location a1 = new Location(bw, a1x, a1y, a1z);
                Location a2 = new Location(bw, a2x, a2y, a2z);

                Claim c = new Claim(owner, a1, a2);

                Object trustedObj = map.get("trusted");
                if (trustedObj instanceof List<?> tlist) {
                    for (Object o : tlist) {
                        if (o == null) continue;
                        try { c.getTrusted().add(UUID.fromString(o.toString())); } catch (Exception ignored) {}
                    }
                }

                add(c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load one claim from data.yml: " + ex.getMessage());
            }
        }
    }

    public void save() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, "data.yml");
        YamlConfiguration yml = new YamlConfiguration();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Claim c : claims.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("owner", c.getOwner().toString());
            map.put("world", c.getWorld());
            map.put("a1x", c.getA1x());
            map.put("a1y", c.getA1y());
            map.put("a1z", c.getA1z());
            map.put("a2x", c.getA2x());
            map.put("a2y", c.getA2y());
            map.put("a2z", c.getA2z());
            map.put("trusted", c.getTrusted().stream().map(UUID::toString).collect(Collectors.toList()));
            list.add(map);
        }

        yml.set("claims", list);

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }
}
