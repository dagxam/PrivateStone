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
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<UUID, Integer> nextNumber = new HashMap<>();

    // Быстрый пространственный индекс: мир + чанк -> только участки, которые могут быть в этом чанке.
    private final Map<String, Set<Claim>> chunkIndex = new HashMap<>();
    private final Map<String, Claim> anchorIndex = new HashMap<>();

    public ClaimManager(PrivateStonePlugin plugin) { this.plugin = plugin; }

    public Collection<Claim> getAll() { return Collections.unmodifiableCollection(claims.values()); }

    public List<Claim> getByOwner(UUID owner) {
        return claims.values().stream().filter(c -> c.getOwner().equals(owner)).collect(Collectors.toList());
    }

    public int allocateNumber(UUID owner) {
        int number = nextNumber.getOrDefault(owner, 1);
        nextNumber.put(owner, number + 1);
        return number;
    }

    public void add(Claim claim) {
        claims.put(makeId(claim), claim);
        index(claim);
        int currentNext = nextNumber.getOrDefault(claim.getOwner(), 1);
        if (claim.getNumber() >= currentNext) nextNumber.put(claim.getOwner(), claim.getNumber() + 1);
    }

    public void remove(Claim claim) {
        claims.remove(makeId(claim));
        unindex(claim);
    }

    public Claim getAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Set<Claim> candidates = chunkIndex.get(chunkKey(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) return null;
        for (Claim claim : candidates) if (claim.contains(location)) return claim;
        return null;
    }

    public Claim getByAnchor(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return anchorIndex.get(anchorKey(location));
    }

    public boolean overlaps(Claim candidate) {
        Set<Claim> checked = new HashSet<>();
        forEachIndexedChunk(candidate, (world, x, z) -> {
            Set<Claim> set = chunkIndex.get(chunkKey(world, x, z));
            if (set != null) checked.addAll(set);
        });
        for (Claim claim : checked) if (claim.overlaps(candidate)) return true;
        return false;
    }

    private void index(Claim claim) {
        forEachIndexedChunk(claim, (world, x, z) ->
                chunkIndex.computeIfAbsent(chunkKey(world, x, z), ignored -> new HashSet<>()).add(claim));
        anchorIndex.put(anchorKey(claim.getWorld(), claim.getA1x(), claim.getA1y(), claim.getA1z()), claim);
        anchorIndex.put(anchorKey(claim.getWorld(), claim.getA2x(), claim.getA2y(), claim.getA2z()), claim);
    }

    private void unindex(Claim claim) {
        forEachIndexedChunk(claim, (world, x, z) -> {
            String key = chunkKey(world, x, z);
            Set<Claim> set = chunkIndex.get(key);
            if (set == null) return;
            set.remove(claim);
            if (set.isEmpty()) chunkIndex.remove(key);
        });
        anchorIndex.remove(anchorKey(claim.getWorld(), claim.getA1x(), claim.getA1y(), claim.getA1z()));
        anchorIndex.remove(anchorKey(claim.getWorld(), claim.getA2x(), claim.getA2y(), claim.getA2z()));
    }

    private interface ChunkConsumer { void accept(String world, int chunkX, int chunkZ); }

    private void forEachIndexedChunk(Claim claim, ChunkConsumer consumer) {
        int minChunkX = claim.getX1() >> 4;
        int maxChunkX = claim.getX2() >> 4;
        int minChunkZ = claim.getZ1() >> 4;
        int maxChunkZ = claim.getZ2() >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) consumer.accept(claim.getWorld(), x, z);
        }
    }

    private String chunkKey(String world, int chunkX, int chunkZ) { return world + ':' + chunkX + ':' + chunkZ; }
    private String anchorKey(Location location) { return anchorKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private String anchorKey(String world, int x, int y, int z) { return world + ':' + x + ':' + y + ':' + z; }

    private String makeId(Claim claim) {
        return claim.getWorld() + ':' + claim.getA1x() + ',' + claim.getA1y() + ',' + claim.getA1z() + '|' +
                claim.getA2x() + ',' + claim.getA2y() + ',' + claim.getA2z();
    }

    public void load() {
        claims.clear(); nextNumber.clear(); chunkIndex.clear(); anchorIndex.clear();
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yml.getMapList("claims")) {
            try {
                UUID owner = UUID.fromString(String.valueOf(map.get("owner")));
                String world = String.valueOf(map.get("world"));
                var bukkitWorld = Bukkit.getWorld(world);
                if (bukkitWorld == null) {
                    plugin.getLogger().warning("Claim skipped: world not found: " + world);
                    continue;
                }
                int a1x = number(map, "a1x"), a1y = number(map, "a1y"), a1z = number(map, "a1z");
                int a2x = number(map, "a2x"), a2y = number(map, "a2y"), a2z = number(map, "a2z");
                int claimNumber = map.get("number") instanceof Number n ? n.intValue() : 1;
                String name = map.get("name") == null ? "Участок " + claimNumber : map.get("name").toString();

                Claim claim = new Claim(owner, new Location(bukkitWorld, a1x, a1y, a1z),
                        new Location(bukkitWorld, a2x, a2y, a2z), claimNumber, name);
                Object trusted = map.get("trusted");
                if (trusted instanceof List<?> list) {
                    for (Object value : list) {
                        try { claim.getTrusted().add(UUID.fromString(String.valueOf(value))); } catch (Exception ignored) { }
                    }
                }
                add(claim);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load one claim from data.yml: " + ex.getMessage());
            }
        }
    }

    private int number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    public void save() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Failed to create plugin data folder.");
            return;
        }
        File file = new File(folder, "data.yml");
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>(claims.size());
        for (Claim claim : claims.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("owner", claim.getOwner().toString()); map.put("world", claim.getWorld());
            map.put("a1x", claim.getA1x()); map.put("a1y", claim.getA1y()); map.put("a1z", claim.getA1z());
            map.put("a2x", claim.getA2x()); map.put("a2y", claim.getA2y()); map.put("a2z", claim.getA2z());
            map.put("number", claim.getNumber()); map.put("name", claim.getName());
            map.put("trusted", claim.getTrusted().stream().map(UUID::toString).collect(Collectors.toList()));
            list.add(map);
        }
        yml.set("claims", list);
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage()); }
    }
}
