package ru.politaboba.serverSystem.item.equipment.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.item.equipment.model.CustomEquipment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EquipmentGiveCommand implements CommandExecutor, TabCompleter {

    private final ServerSystem plugin;

    public EquipmentGiveCommand(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверка прав (измени на свое, если нужно)
        if (!sender.hasPermission("serversystem.admin.giveequipment")) {
            sender.sendMessage("§c§lОшибка! §7У вас нет прав на использование этой команды.");
            return true;
        }

        // Проверка синтаксиса
        if (args.length < 1) {
            sender.sendMessage("§c§lИспользование: §7/rpgive <id_предмета> [игрок]");
            return true;
        }

        String equipmentId = args[0];
        CustomEquipment equipment = plugin.getEquipmentManager().getEquipmentById(equipmentId);

        // Если предмет с таким ID не зарегистрирован в EquipmentManager
        if (equipment == null) {
            sender.sendMessage("§c§lОшибка! §7Кастомный предмет с ID §e" + equipmentId + " §7не найден.");
            return true;
        }

        // Определение целевого игрока
        Player targetPlayer;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage("§c§lОшибка! §7Игрок §e" + args[1] + " §7не найден или находится оффлайн.");
                return true;
            }
        } else {
            // Если игрок не указан, проверяем, что команду ввел игрок, а не консоль
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c§lОшибка! §7При отправке из консоли необходимо указывать ник: /rpgive <id> <игрок>");
                return true;
            }
            targetPlayer = (Player) sender;
        }

        // Создаем ItemStack через твою OOP-систему
        ItemStack itemStack = equipment.createItemStack(plugin);

        // Пытаемся выдать предмет. Если инвентарь полон — дропаем под ноги
        if (!targetPlayer.getInventory().addItem(itemStack).isEmpty()) {
            targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), itemStack);
            targetPlayer.sendMessage("§e§oВаш инвентарь был полон, предмет упал на землю!");
        }

        // Уведомления об успехе
        String displayName = itemStack.getItemMeta() != null && itemStack.getItemMeta().hasDisplayName()
                ? itemStack.getItemMeta().getDisplayName()
                : itemStack.getType().name();

        sender.sendMessage("§a§lУспех! §fПредмет " + displayName + " §fуспешно выдан игроку §e" + targetPlayer.getName());

        if (!targetPlayer.equals(sender)) {
            targetPlayer.sendMessage("§d§lАдминистрация §fвыдала вам предмет: " + displayName);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serversystem.admin.giveequipment")) {
            return new ArrayList<>();
        }

        // Первый аргумент: Автоподстановка ID предметов из EquipmentManager
        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            return plugin.getEquipmentManager().getRegisteredIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        // Второй аргумент: Список онлайн игроков
        if (args.length == 2) {
            String currentArg = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}