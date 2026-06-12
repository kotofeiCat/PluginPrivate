package ru.politaboba.serverSystem.cases;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CaseCommand implements CommandExecutor {

    private final CaseManager caseManager;

    public static class CaseGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public CaseCommand(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("givecoins")) {
            if (!sender.hasPermission("serversystem.admin")) {
                sender.sendMessage("§cУ вас нет прав на выдачу донат-валюты!");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cИспользование: /case givecoins <ник> <число>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cИгрок оффлайн или не найден.");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage("§cУкажите корректное положительное число!");
                return true;
            }

            caseManager.addCoins(target.getUniqueId(), amount);
            sender.sendMessage("§aВы выдали §e" + amount + " Поликоинов §aигроку §b" + target.getName());
            target.sendMessage("§a§l[Донат] §fНа ваш баланс зачислено §e" + amount + " Поликоинов§f! Потратить: §6/case");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;
        openCaseMenu(player);
        return true;
    }

    private void openCaseMenu(Player player) {
        Inventory gui = Bukkit.createInventory(new CaseGuiHolder(), 27, "§0Торговый Терминал: Кейсы");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }

        int balance = caseManager.getCoins(player.getUniqueId());
        ItemStack info = new ItemStack(Material.EMERALD);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§a§lВаш Счёт");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Текущий баланс личного кабинета:");
        infoLore.add("§e" + balance + " §dПоликоинов (Валюта)");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // КЕЙС 1: ОБЫЧНЫЙ
        ItemStack commonCase = new ItemStack(Material.CHEST);
        ItemMeta cMeta = commonCase.getItemMeta();
        cMeta.setDisplayName("§f§lОбычный Кейс");
        List<String> cLore = new ArrayList<>();
        cLore.add("§7Содержит базовые припасы и ресурсы.");
        cLore.add("");
        cLore.add("§7Возможный лут:");
        cLore.add(" §7• Ресурсы (Уголь, Железо)");
        cLore.add(" §7• Кастомная еда (Стейк, Шашлык, Карп)");
        cLore.add(" §e• Низкий шанс: §aАлмазы, Незерит, Алебарда");
        cLore.add("");
        cLore.add("§7Стоимость: §e25 Поликоинов");
        cLore.add("§a[Нажми, чтобы приобрести и открыть]");
        cMeta.setLore(cLore);
        commonCase.setItemMeta(cMeta);
        gui.setItem(10, commonCase);

        // КЕЙС 2: РЕДКИЙ
        ItemStack rareCase = new ItemStack(Material.TRAPPED_CHEST);
        ItemMeta rMeta = rareCase.getItemMeta();
        rMeta.setDisplayName("§b§lРедкий Кейс");
        List<String> rLore = new ArrayList<>();
        rLore.add("§7Повышенные шансы на редкие ресурсы.");
        rLore.add("");
        rLore.add("§7Возможный лут:");
        rLore.add(" §7• Предметы обычного кейса (Повышенный шанс)");
        rLore.add(" §7• Слитки незерита");
        rLore.add(" §7• Книги зачарования IV уровня");
        rLore.add("");
        rLore.add("§7Стоимость: §e50 Поликоинов");
        rLore.add("§a[Нажми, чтобы приобрести и открыть]");
        rMeta.setLore(rLore);
        rareCase.setItemMeta(rMeta);
        gui.setItem(12, rareCase);

        // КЕЙС 3: ЭПИЧЕСКИЙ
        ItemStack epicCase = new ItemStack(Material.ENDER_CHEST);
        ItemMeta eMeta = epicCase.getItemMeta();
        eMeta.setDisplayName("§5§lЭпический Кейс");
        List<String> eLore = new ArrayList<>();
        eLore.add("§7Магические фолианты и мощное вооружение.");
        eLore.add("");
        eLore.add("§7Возможный лут:");
        eLore.add(" §7• Ценные ресурсы (Блоки алмазов, Незерит)");
        eLore.add(" §7• Книги на Защиту, Остроту, Силу VI уровня");
        eLore.add(" §7• §6Лук Бури / Топор Раскалывателя Замков");
        eLore.add("");
        eLore.add("§7Стоимость: §e100 Поликоинов");
        eLore.add("§a[Нажми, чтобы приобрести и открыть]");
        eMeta.setLore(eLore);
        epicCase.setItemMeta(eMeta);
        gui.setItem(14, epicCase);

        // КЕЙС 4: ЛЕГЕНДАРНЫЙ
        ItemStack legendaryCase = new ItemStack(Material.BEACON);
        ItemMeta lMeta = legendaryCase.getItemMeta();
        lMeta.setDisplayName("§6§lЛегендарный Кейс");
        List<String> lLore = new ArrayList<>();
        lLore.add("§7Полный арсенал высшего кастомного оружия.");
        lLore.add("");
        lLore.add("§7Возможный лут:");
        lLore.add(" §7• §bКопьё Зефира / Магнитный Палаш");
        lLore.add(" §7• §bЛук Бури / Топор Раскалывателя Замков");
        lLore.add(" §e• Маленький шанс: §cШаблон брони Ловца Ветров");
        lLore.add("");
        lLore.add("§7Стоимость: §e300 Поликоинов");
        lLore.add("§a[Нажми, чтобы приобрести и открыть]");
        lMeta.setLore(lLore);
        legendaryCase.setItemMeta(lMeta);
        gui.setItem(16, legendaryCase);

        player.openInventory(gui);
    }
}