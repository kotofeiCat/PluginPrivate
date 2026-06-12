package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TestCastleCommand implements CommandExecutor {

    private final DungeonManager dungeonManager;

    public TestCastleCommand(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эту команду может использовать только игрок!");
            return true;
        }

        if (!player.hasPermission("dungeon.admin")) {
            player.sendMessage("§cУ вас нет прав!");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(15);
        if (targetBlock == null) {
            player.sendMessage("§cПожалуйста, посмотрите на блок земли, где должен начаться замок!");
            return true;
        }

        Location startLocation = targetBlock.getLocation();
        player.sendMessage("§a[Данж] Запуск генерации Замка из NBT схемы...");

        // ИСПРАВЛЕНО: Генерируем ID сессии и передаем его в метод createDungeon, убирая ошибку компиляции
        String dungeonId = "castle_admin_" + player.getName().toLowerCase();
        dungeonManager.createDungeon(dungeonId, startLocation);

        player.sendMessage("§e[Успех] Замок построен. Зона лобби ожидает активации!");
        return true;
    }
}