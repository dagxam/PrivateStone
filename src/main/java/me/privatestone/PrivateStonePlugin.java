package me.privatestone;

import org.bukkit.plugin.java.JavaPlugin;

public class PrivateStonePlugin extends JavaPlugin {

    public ClaimManager claims;
    public boolean allowOverlap;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        allowOverlap = getConfig().getBoolean("allowOverlap");

        claims = new ClaimManager();
        getServer().getPluginManager().registerEvents(
                new ProtectListener(this), this
        );
    }
}
