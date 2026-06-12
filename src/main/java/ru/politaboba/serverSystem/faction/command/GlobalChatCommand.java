package ru.politaboba.serverSystem.faction.command;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.manager.ChatManager;
import ru.politaboba.serverSystem.faction.model.Faction;

public class GlobalChatCommand implements CommandExecutor {

    private final ServerSystem plugin;
    private final boolean hasPlaceholderAPI;

    public GlobalChatCommand(ServerSystem plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.hasPlaceholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        // ГЛОБАЛЬНЫЙ ЧАТ ЧЕРЕЗ /g
        if (label.equalsIgnoreCase("g")) {
            if (args.length == 0) {
                player.sendMessage("§cИспользование: /g <сообщение> или пишите знак ! перед текстом в чате.");
                return true;
            }
            String message = String.join(" ", args);
            String factionName = plugin.getPlayerFactionMap().get(player.getUniqueId());

            // 1. Спонсорский префикс из LuckPerms
            String lpPrefix = "";
            if (hasPlaceholderAPI) {
                lpPrefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
            }

            // 2. Префикс фракции
            String factionPrefix = "§8[Скиталец] ";
            if (factionName != null) {
                Faction faction = plugin.getFactions().get(factionName);
                ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GOLD;
                factionPrefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + color;
            }

            // Сборка сообщения
            String finalMessage = ChatColor.translateAlternateColorCodes('&',
                    "§e📢 [ГЛОБАЛ] " + lpPrefix + factionPrefix + player.getName() + "§7: §f" + message);

            Bukkit.broadcastMessage(finalMessage);
            return true;
        }

        // ЛИЧНЫЕ СООБЩЕНИЯ (/msg, /w)
        if (label.equalsIgnoreCase("msg") || label.equalsIgnoreCase("w")) {
            if (args.length < 2) {
                player.sendMessage("§cИспользование: /msg <ник> <сообщение>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cИгрок не найден или оффлайн.");
                return true;
            }

            String[] messageArgs = new String[args.length - 1];
            System.arraycopy(args, 1, messageArgs, 0, messageArgs.length);
            String privateMessage = String.join(" ", messageArgs);

            player.sendMessage("§d[Я -> " + target.getName() + "] §f" + privateMessage);
            target.sendMessage("§d[" + player.getName() + " -> Я] §f" + privateMessage);
            return true;
        }

        return false;
    }
}