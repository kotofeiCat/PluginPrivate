package ru.politaboba.serverSystem.Dangeon.PobornikCastle;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class TestCastleCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final CastleGenerator generator;
    private final ArchVindicatorBoss bossMechanics;

    public TestCastleCommand(JavaPlugin plugin, ArchVindicatorBoss bossMechanics) {
        this.plugin = plugin;
        this.generator = new CastleGenerator(plugin);
        this.bossMechanics = bossMechanics;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Проверяем, что команду ввел игрок, а не консоль
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эту команду может использовать только игрок!");
            return true;
        }

        // Проверяем права (опционально)
        if (!player.hasPermission("dungeon.admin")) {
            player.sendMessage("У вас нет прав!");
            return true;
        }

        // Получаем блок, на который смотрит игрок (в радиусе 10 блоков)
        Block targetBlock = player.getTargetBlockExact(10);
        if (targetBlock == null) {
            player.sendMessage("Пожалуйста, посмотрите на блок на земле, где начать строить!");
            return true;
        }

        Location startLocation = targetBlock.getLocation();
        player.sendMessage("§aНачинается генерация Тронного зала...");

        // ЗАПУСК: строим структуру, а по окончании спавним босса
        generator.generateThroneRoomAsync(startLocation, () -> {
            // Этот код выполнится строго ПОСЛЕ того, как выставится последний блок
            player.sendMessage("§e[Успех] Тронный зал построен! Спавним Верховного Инквизитора Малакая...");

            // Спавним босса чуть выше центра структуры (например, со смещением X=7, Y=2, Z=7)
            Location bossLocation = startLocation.clone().add(7, 2, 7);
            bossMechanics.spawn(bossLocation);
        });

        return true;
    }
}
