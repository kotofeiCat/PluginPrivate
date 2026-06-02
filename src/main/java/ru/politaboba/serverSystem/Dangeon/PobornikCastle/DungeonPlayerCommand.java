package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DungeonPlayerCommand implements CommandExecutor {

    private final DungeonManager dungeonManager;

    public DungeonPlayerCommand(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эту команду могут использовать только игроки!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cИспользуйте: /dungeon ready — подтвердить готовность в лобби");
            player.sendMessage("§cИспользуйте: /dungeon start — запустить данж (доступно только лидеру комнаты)");
            return true;
        }

        // Ищем сессию данжа, находящуюся поблизости от игрока
        DungeonSession currentSession = dungeonManager.getNearestSession(player.getLocation());
        if (currentSession == null) {
            player.sendMessage("§cПоблизости не обнаружено активных сессий данжей.");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("ready") || subCommand.equals("join")) {
            currentSession.handlePlayerReady(player);
            return true;
        }

        if (subCommand.equals("start")) {
            currentSession.handleForceStart(player);
            return true;
        }

        player.sendMessage("§cНеизвестная подкоманда! Используйте ready или start.");
        return true;
    }
}