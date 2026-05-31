package ru.politaboba.serverSystem.arrest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArrestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (!player.hasPermission("serversystem.admin")) {
            player.sendMessage("§cУ вас нет прав!");
            return true;
        }

        player.getInventory().addItem(ArrestManager.createHandcuffs());
        player.sendMessage("§aВы получили РП-Кандалы. Выдайте их стражам порядка или наемникам!");
        return true;
    }
}