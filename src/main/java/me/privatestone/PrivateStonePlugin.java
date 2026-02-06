package me.privatestone;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class PrivateStonePlugin extends JavaPlugin {
    private ClaimManager claimManager;

    private Material claimBlock;
    private boolean allowOverlap;
    private int maxSide;
    private boolean protectFromExplosions;

    private NamespacedKey claimItemKey;

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

        getLogger().info("PrivateStone enabled. Claims loaded: " + claimManager.getAll().size());
    }

    @Override
    public void onDisable() {
        if (claimManager != null) claimManager.save();
        getLogger().info("PrivateStone disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        loadSettings();
    }

    private void loadSettings() {
        FileConfiguration c = getConfig();

        this.claimBlock = Material.matchMaterial(c.getString("claimBlock", "STONE"));
        if (this.claimBlock == null) this.claimBlock = Material.STONE;

        this.allowOverlap = c.getBoolean("allowOverlap", false);
        this.maxSide = Math.max(1, c.getInt("maxSide", 128));
        this.protectFromExplosions = c.getBoolean("protectFromExplosions", true);

        this.claimItemKey = new NamespacedKey(this, "privatestone_claim_item");
    }

    public ClaimManager claims() { return claimManager; }

    public Material getClaimBlock() { return claimBlock; }
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

    public ItemStack createClaimItem(int amount) {
        ItemStack it = new ItemStack(getClaimBlock(), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.c(getConfig().getString("claimItem.name", "&aPrivateStone")));
            meta.setLore(Text.c(getConfig().getStringList("claimItem.lore")));
            meta.getPersistentDataContainer().set(getClaimItemKey(), PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public boolean isClaimItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (it.getType() != getClaimBlock()) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(getClaimItemKey(), PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }
}
