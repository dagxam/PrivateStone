package me.privatestone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PrivateStonePlugin extends JavaPlugin {
    private ClaimManager claimManager;

    private final Set<Material> claimBlocks = EnumSet.noneOf(Material.class);
    private boolean allowOverlap;
    private int maxSide;
    private boolean protectFromExplosions;

    private NamespacedKey claimItemKey;
    private final Map<Material, NamespacedKey> recipeKeys = new EnumMap<>(Material.class);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        this.claimManager = new ClaimManager(this);
        this.claimManager.load();

        getServer().getPluginManager().registerEvents(new ProtectListener(this), this);

        var cmd = new PStoneCommand(this);
        if (getCommand("pstone") != null) {
            getCommand("pstone").setExecutor(cmd);
            getCommand("pstone").setTabCompleter(cmd);
        }

        registerRecipes();
        getLogger().info("PrivateStone enabled. Claims loaded: " + claimManager.getAll().size());
    }

    @Override
    public void onDisable() {
        if (claimManager != null) claimManager.save();
        unregisterRecipes();
        getLogger().info("PrivateStone disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        unregisterRecipes();
        loadSettings();
        registerRecipes();
    }

    private void loadSettings() {
        FileConfiguration c = getConfig();
        claimBlocks.clear();

        for (String raw : c.getStringList("claimBlocks")) {
            Material material = Material.matchMaterial(raw);
            if (isValidClaimMaterial(material)) claimBlocks.add(material);
            else getLogger().warning("Invalid claim block in config: " + raw);
        }

        if (claimBlocks.isEmpty()) {
            Material legacy = Material.matchMaterial(c.getString("claimBlock", "STONE"));
            claimBlocks.add(legacy == null ? Material.STONE : legacy);
        }

        this.allowOverlap = c.getBoolean("allowOverlap", false);
        this.maxSide = Math.max(1, c.getInt("maxSide", 128));
        this.protectFromExplosions = c.getBoolean("protectFromExplosions", true);
        this.claimItemKey = new NamespacedKey(this, "privatestone_claim_item");
    }

    private boolean isValidClaimMaterial(Material material) {
        return material != null && material.isBlock() && !material.isAir();
    }

    public ItemStack createClaimItem(int amount) {
        return createClaimItem(getDefaultClaimBlock(), amount);
    }

    public ItemStack createClaimItem(Material material, int amount) {
        if (!isClaimBlock(material)) material = getDefaultClaimBlock();

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String baseName = getConfig().getString("claimItem.name", "&aБлок привата");
            meta.setDisplayName(Text.c(baseName.replace("%block%", formatMaterial(material))));
            meta.setLore(Text.c(getConfig().getStringList("claimItem.lore")));
            meta.getPersistentDataContainer().set(claimItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isClaimItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !isClaimBlock(item.getType())) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(claimItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void registerRecipes() {
        unregisterRecipes();
        for (Material material : claimBlocks) {
            NamespacedKey key = new NamespacedKey(this, "privatestone_" + material.getKey().getKey().toLowerCase(Locale.ROOT));
            recipeKeys.put(material, key);

            ShapedRecipe recipe = new ShapedRecipe(key, createClaimItem(material, 1));
            recipe.shape("BBB", "BBB", "BBB");
            recipe.setIngredient('B', material);
            Bukkit.addRecipe(recipe);
        }
    }

    private void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys.values()) Bukkit.removeRecipe(key);
        recipeKeys.clear();
    }

    public boolean isClaimBlock(Material material) {
        return material != null && claimBlocks.contains(material);
    }

    public Set<Material> getClaimBlocks() {
        return Collections.unmodifiableSet(claimBlocks);
    }

    public Material getDefaultClaimBlock() {
        return claimBlocks.contains(Material.STONE) ? Material.STONE : claimBlocks.iterator().next();
    }

    private String formatMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public ClaimManager claims() { return claimManager; }
    public boolean isAllowOverlap() { return allowOverlap; }
    public int getMaxSide() { return maxSide; }
    public boolean isProtectFromExplosions() { return protectFromExplosions; }
    public NamespacedKey getClaimItemKey() { return claimItemKey; }

    public String msg(String path) {
        return Text.c(getConfig().getString("messages." + path, ""));
    }

    public List<String> msgList(String path) {
        return Text.c(getConfig().getStringList("messages." + path));
    }
}
