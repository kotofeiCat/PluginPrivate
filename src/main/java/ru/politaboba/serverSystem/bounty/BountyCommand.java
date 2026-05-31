package ru.politaboba.serverSystem.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BountyCommand implements CommandExecutor {

    private final ServerSystem plugin;

    public BountyCommand(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;

        // Если введена просто команда /bounty — открываем меню доски
        if (args.length == 0) {
            openBountyBoard(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("board")) {
            openBountyBoard(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("target") && args.length > 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cИгрок не найден или оффлайн.");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage("§cУкажите корректное число алмазов!");
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            if (plugin.getBountyOrders().containsKey(targetUUID)) {
                player.sendMessage("§cНа этого игрока уже есть активный заказ!");
                return true;
            }

            // Проверяем, есть ли у игрока нужное количество алмазов
            if (!hasEnoughDiamonds(player, amount)) {
                player.sendMessage("§cУ вас нет столько алмазов в инвентаре!");
                return true;
            }

            // Забираем алмазы
            removeDiamonds(player, amount);

            // Создаем заказ
            BountyOrder order = new BountyOrder(target.getName(), targetUUID, player.getName(), amount);
            plugin.getBountyOrders().put(targetUUID, order);

            Bukkit.broadcastMessage("§c§l[Охота за Головами] §e" + player.getName() + " §7назначил награду за голову §c" + target.getName() + " §7в размере §b" + amount + " алмазов§7!");
            return true;
        }

        return false;
    }

    private void openBountyBoard(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§0Доска Заказов");

        // Кнопка-инструкция
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e§lБыстрое создание заказа");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Нажмите сюда, чтобы автоматически");
        infoLore.add("§7сформировать команду в чате.");
        infoLore.add("");
        infoLore.add("§b👉 Нажми, чтобы скопировать: §f/bounty target");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // Кнопка сдачи контракта
        ItemStack claim = new ItemStack(Material.CHEST);
        ItemMeta claimMeta = claim.getItemMeta();
        claimMeta.setDisplayName("§aСдать контракт");
        List<String> claimLore = new ArrayList<>();
        claimLore.add("§7Кликните сюда, держа в руке");
        claimLore.add("§7голову жертвы, чтобы забрать награду.");
        claimMeta.setLore(claimLore);
        claim.setItemMeta(claimMeta);
        gui.setItem(40, claim);

        // Выводим головы жертв
        int slot = 9;
        for (BountyOrder order : plugin.getBountyOrders().values()) {
            if (slot >= 36) break; // Ограничение инвентаря

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) playerHead.getItemMeta();

            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(order.getTargetUUID()));
            headMeta.setDisplayName("§cЦель: §l" + order.getTargetName());

            List<String> lore = new ArrayList<>();
            lore.add("§7Награда: §b" + order.getRewardAmount() + " Алмазов");
            lore.add("§7Заказчик: §e" + order.getCreatorName());
            headMeta.setLore(lore);
            playerHead.setItemMeta(headMeta);

            gui.setItem(slot, playerHead);
            slot++;
        }

        player.openInventory(gui);
    }

    private boolean hasEnoughDiamonds(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeDiamonds(Player player, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.DIAMOND) {
                if (item.getAmount() > left) {
                    item.setAmount(item.getAmount() - left);
                    break;
                } else {
                    left -= item.getAmount();
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setContents(contents);
    }
}