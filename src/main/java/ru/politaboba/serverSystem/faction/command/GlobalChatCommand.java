package ru.politaboba.serverSystem.faction.command;

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
    private final ChatManager chatManager;

    public GlobalChatCommand(ServerSystem plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        // Команда /g <текст> (Глобальный чат)
        if (label.equalsIgnoreCase("g")) {
            if (args.length == 0) {
                player.sendMessage("§cИспользование: /g <сообщение> или пишите знак ! перед текстом в обычном чате.");
                return true;
            }
            String message = String.join(" ", args);
            String factionName = plugin.getPlayerFactionMap().get(player.getUniqueId());

            String prefix = "§8[Скиталец] ";
            if (factionName != null) {
                Faction faction = plugin.getFactions().get(factionName);
                ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GOLD;
                prefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + color;
            }

            Bukkit.broadcastMessage("§e📢 [ГЛОБАЛ] " + prefix + player.getName() + "§7: §f" + message);
            return true;
        }

        // Команда /msg <ник> <текст>
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

        // Команда /f chat или /faction chat (Переключение режимов)
        if ((label.equalsIgnoreCase("faction") || label.equalsIgnoreCase("f")) && args.length > 0 && args[0].equalsIgnoreCase("chat")) {
            String factionName = plugin.getPlayerFactionMap().get(player.getUniqueId());
            if (factionName == null) {
                player.sendMessage("§cВы должны состоять во фракции, чтобы включить зашифрованный радиоканал!");
                return true;
            }

            chatManager.toggleChatMode(player.getUniqueId());
            ChatManager.ChatMode newMode = chatManager.getChatMode(player.getUniqueId());

            if (newMode == ChatManager.ChatMode.FACTION) {
                player.sendMessage("§a[РАДИО] Вы подключились к зашифрованной частоте фракции §e" + factionName + "§a. Все ваши сообщения теперь видят только союзники.");
            } else {
                player.sendMessage("§7[ЧАТ] Вы вернулись на открытую частоту (локальный радиус 50 блоков).");
            }
            return true;
        }

        return false;
    }
}