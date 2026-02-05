package me.privatestone;

import org.bukkit.command.*;

public class PStoneCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        sender.sendMessage("PrivateStone работает");
        return true;
    }
}
