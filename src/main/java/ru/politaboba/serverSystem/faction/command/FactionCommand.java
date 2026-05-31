package ru.politaboba.serverSystem.faction.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.model.Faction;
import ru.politaboba.serverSystem.faction.model.FactionDepartment;
import ru.politaboba.serverSystem.faction.model.FactionRank;
import ru.politaboba.serverSystem.faction.model.FactionPermission;
import ru.politaboba.serverSystem.faction.manager.PassportManager;
import ru.politaboba.serverSystem.item.ItemFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FactionCommand implements CommandExecutor {

    private final ServerSystem plugin;
    private final ItemFactory itemFactory; // Оптимизация: держим фабрику в памяти
    private static final java.util.Map<UUID, String> invites = new java.util.HashMap<>();

    public FactionCommand(ServerSystem plugin) {
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            openFactionMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Слой 1: Команды, доступные БЕЗ проверки на членство во фракции
        switch (subCommand) {
            case "help": sendHelpMessage(player); return true;
            case "cookbook": executeCookbook(player); return true;
            case "create": executeCreate(player, args); return true;
            case "accept": executeAccept(player); return true;
            case "list": executeList(player); return true; // Новая команда
            case "info": executeInfo(player, args); return true; // Новая команда
        }

        // Слой 2: Глобальная проверка наличия фракции для всех остальных действий
        UUID playerUUID = player.getUniqueId();
        String currentFactionName = plugin.getPlayerFactionMap().get(playerUUID);
        if (currentFactionName == null) {
            player.sendMessage("§cВы не состоите во фракции! Используйте §e/f create <имя>§c или §e/f help");
            return true;
        }

        Faction faction = plugin.getFactions().get(currentFactionName);
        if (faction == null) {
            player.sendMessage("§cОшибка: Ваша фракция не найдена в реестре системы.");
            return true;
        }

        // Слой 3: Маршрутизация внутрифракционных команд
        switch (subCommand) {
            case "leave": executeLeave(player, faction); return true; // Новая команда
            case "disband": executeDisband(player, faction); return true; // Новая команда
            case "invite": executeInvite(player, faction, currentFactionName, args); return true;
            case "kick": executeKick(player, faction, currentFactionName, args); return true;
            case "balance": executeBalance(player, faction); return true;
            case "deposit": executeDeposit(player, faction, args); return true;
            case "withdraw": executeWithdraw(player, faction, args); return true;
            case "rank": executeRank(player, faction, args); return true;
            case "dept": executeDept(player, faction, args); return true;
            case "assign": executeAssign(player, faction, currentFactionName, args); return true;
            case "passport": executePassport(player, faction, currentFactionName, args); return true;
            case "addenemy": executeAddEnemy(player, faction, currentFactionName, args); return true;
            case "color": executeColor(player, faction, args); return true;
        }

        player.sendMessage("§cНеизвестная подкоманда. Используйте §e/f help");
        return true;
    }

    // =========================================================================
    // ЛОГИКА ПОДКОМАНД (ВЫНЕСЕНА В ОДДЕЛЬНЫЕ МЕТОДЫ)
    // =========================================================================


    private void executeColor(Player player, Faction faction, String[] args) {
        // Проверяем, является ли игрок лидером фракции
        if (!player.getUniqueId().equals(faction.getLeader())) {
            player.sendMessage("§cТолько Верховный Лидер государства может менять его официальный цвет!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cИспользование: §e/f color <ЦВЕТ>");
            player.sendMessage("§7Доступные цвета: §fRED, §aGREEN, §bAQUA, §eYELLOW, §dLIGHT_PURPLE, §6GOLD, §7GRAY, §9BLUE");
            return;
        }

        String inputColor = args[1].toUpperCase();
        org.bukkit.ChatColor chosenColor;

        // Валидация введенного цвета, чтобы сервер не упал от неверного текста
        try {
            chosenColor = org.bukkit.ChatColor.valueOf(inputColor);

            // Запрещаем технические или нечитаемые цвета (например, черный, жирный, подчеркнутый)
            if (chosenColor.isFormat() || chosenColor == org.bukkit.ChatColor.BLACK) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cНеверный цвет! Выберите из списка: §fRED, GREEN, AQUA, YELLOW, LIGHT_PURPLE, GOLD, GRAY, BLUE");
            return;
        }

        // Предполагается, что в твоем классе Faction есть метод setFactionColor(ChatColor color)
        // Если метод называется по-другому, подправь под свою модель.
        faction.setFactionColor(chosenColor);

        // Принудительно обновляем префиксы в Табе и Скорборде над головой для всех игроков
        plugin.updateAllPlayersDisplay();

        player.sendMessage("§a[Реестр] Официальный цвет вашего государства успешно изменен на " + chosenColor + chosenColor.name() + "§a!");
    }

    private void executeCookbook(Player player) {
        if (!player.isOp()) {
            player.sendMessage("§cУ вас нет прав на использование этой команды!");
            return;
        }
        player.getInventory().addItem(itemFactory.createCookbook());
        player.sendMessage("§aВы успешно выдали себе §6Кулинарную Книгу Рецептов§a!");
    }

    private void executeCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f create <Название>");
            return;
        }
        UUID playerUUID = player.getUniqueId();
        String factionName = args[1];

        if (plugin.getPlayerFactionMap().containsKey(playerUUID)) {
            player.sendMessage("§cВы уже состоите во фракции!");
            return;
        }
        if (plugin.getFactions().containsKey(factionName)) {
            player.sendMessage("§cФракция с таким названием уже существует!");
            return;
        }

        Faction newFaction = new Faction(factionName, playerUUID);
        plugin.getFactions().put(factionName, newFaction);
        plugin.getPlayerFactionMap().put(playerUUID, factionName);

        plugin.updateAllPlayersDisplay();
        player.sendMessage("§aФракция §e" + factionName + " §aуспешно создана!");
    }

    private void executeAccept(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (plugin.getPlayerFactionMap().containsKey(playerUUID)) {
            player.sendMessage("§cВы уже состоите в государстве!");
            invites.remove(playerUUID);
            return;
        }

        String targetFactionName = invites.get(playerUUID);
        if (targetFactionName == null) {
            player.sendMessage("§cУ вас нет активных приглашений в государства.");
            return;
        }

        Faction targetFaction = plugin.getFactions().get(targetFactionName);
        if (targetFaction == null) {
            player.sendMessage("§cОшибка: Государство, в которое вас звали, распалось.");
            invites.remove(playerUUID);
            return;
        }

        targetFaction.assignPlayer(playerUUID, "Жители", "Крестьянин");
        plugin.getPlayerFactionMap().put(playerUUID, targetFactionName);
        invites.remove(playerUUID);

        plugin.updateAllPlayersDisplay();
        player.sendMessage("§aВы успешно приняли приглашение и вступили в §e" + targetFactionName + "§a!");

        for (UUID memberUUID : targetFaction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage("§6[Реестр] Гражданин §e" + player.getName() + " §aпринял присягу и вступил в наши ряды!");
            }
        }
    }

    private void executeList(Player player) {
        player.sendMessage("§6▬▬▬▬▬▬▬▬▬ §l[ РЕЕСТР ФРАКЦИЙ СЕРВЕРА ] §6▬▬▬▬▬▬▬▬▬");
        if (plugin.getFactions().isEmpty()) {
            player.sendMessage("§7На сервере пока нет созданных государств.");
            return;
        }
        for (Faction f : plugin.getFactions().values()) {
            player.sendMessage("§7• §b" + f.getName() + " §e| §7Граждан: §a" + f.getMembers().size() + " §e| §7Лидер: §6" + Bukkit.getOfflinePlayer(f.getLeader()).getName());
        }
    }

    private void executeInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f info <Название Государства>");
            return;
        }
        Faction target = plugin.getFactions().get(args[1]);
        if (target == null) {
            player.sendMessage("§cГосударство с таким названием не найдено.");
            return;
        }
        player.sendMessage("§6=== Государева справка: §b" + target.getName() + " §6===");
        player.sendMessage("§eВерховный Лидер: §f" + Bukkit.getOfflinePlayer(target.getLeader()).getName());
        player.sendMessage("§eЧисленность населения: §a" + target.getMembers().size() + " чел.");
        player.sendMessage("§eКоличество ведомств: §f" + target.getDepartments().size());
    }

    private void executeLeave(Player player, Faction faction) {
        UUID playerUUID = player.getUniqueId();
        if (playerUUID.equals(faction.getLeader())) {
            player.sendMessage("§cЛидер не может покинуть государство! Сначала передайте пост или распустите его через §e/f disband");
            return;
        }

        faction.removePlayer(playerUUID);
        plugin.getPlayerFactionMap().remove(playerUUID);
        plugin.updateAllPlayersDisplay();

        player.sendMessage("§cВы добровольно покинули ряды государства §e" + faction.getName() + "§c и снова стали скитальцем.");
        for (UUID memberUUID : faction.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage("§7[Реестр] §e" + player.getName() + " §7оформил эмиграцию и покинул нашу страну.");
            }
        }
    }

    private void executeDisband(Player player, Faction faction) {
        if (!player.getUniqueId().equals(faction.getLeader())) {
            player.sendMessage("§cТолько Верховный Лидер может уничтожить государство!");
            return;
        }

        String factionName = faction.getName();

        // Оповещаем и исключаем всех граждан
        for (UUID memberUUID : faction.getMembers()) {
            plugin.getPlayerFactionMap().remove(memberUUID);
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage("§c§lГосударство §e" + factionName + " §c§lбыло полностью распущено лидером!");
            }
        }

        plugin.getFactions().remove(factionName);
        plugin.updateAllPlayersDisplay();
        player.sendMessage("§aВы успешно распустили империю §e" + factionName + "§a. История окончена.");
    }

    private void executeInvite(Player player, Faction faction, String factionName, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f invite <Ник>");
            return;
        }
        UUID playerUUID = player.getUniqueId();
        boolean isLeader = playerUUID.equals(faction.getLeader());
        boolean hasInvitePerm = faction.hasPermission(playerUUID, FactionPermission.INVITE);

        if (!isLeader && !hasInvitePerm) {
            player.sendMessage("§cУ вас нет прав на приглашение людей!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cИгрок не найден или оффлайн.");
            return;
        }

        UUID targetUUID = target.getUniqueId();
        if (plugin.getPlayerFactionMap().containsKey(targetUUID)) {
            player.sendMessage("§cЭтот игрок уже состоит в каком-то государстве!");
            return;
        }

        if (faction.isBlacklisted(targetUUID)) {
            player.sendMessage("§cЭтот игрок находится в ЧС государства!");
            return;
        }

        invites.put(targetUUID, factionName);
        player.sendMessage("§aПриглашение отправлено игроку §e" + target.getName() + "§a.");

        target.sendMessage(" ");
        target.sendMessage("§6★ Вас приглашают вступить в государство §b" + factionName + "§6!");
        target.sendMessage("§6★ Используйте §a/f accept §6для подтверждения вступления.");
        target.sendMessage(" ");
    }

    private void executeKick(Player player, Faction faction, String currentFactionName, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f kick <Ник>");
            return;
        }
        UUID playerUUID = player.getUniqueId();
        boolean isLeader = playerUUID.equals(faction.getLeader());
        boolean hasKickPerm = faction.hasPermission(playerUUID, FactionPermission.KICK);

        if (!isLeader && !hasKickPerm) {
            player.sendMessage("§cВаша должность не имеет полномочий исключать граждан!");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetUUID = target.getUniqueId();

        String targetFaction = plugin.getPlayerFactionMap().get(targetUUID);
        if (targetFaction == null || !targetFaction.equals(currentFactionName)) {
            player.sendMessage("§cЭтот игрок не является гражданином вашего государства!");
            return;
        }

        if (targetUUID.equals(faction.getLeader())) {
            player.sendMessage("§cНельзя исключить Верховного Лидера!");
            return;
        }

        if (targetUUID.equals(playerUUID)) {
            player.sendMessage("§cВы не можете кикнуть самого себя.");
            return;
        }

        if (!isLeader) {
            for (FactionDepartment dept : faction.getDepartments().values()) {
                if (targetUUID.equals(dept.getDepartmentHead())) {
                    player.sendMessage("§cВы не можете исключить Главу Ведомства! Это может сделать только Лидер.");
                    return;
                }
            }
        }

        faction.removePlayer(targetUUID);
        plugin.getPlayerFactionMap().remove(targetUUID);
        plugin.updateAllPlayersDisplay();

        player.sendMessage("§aГражданин §e" + target.getName() + " §cбыл позорно изгнан!");
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("§c§lВы были исключены из государства §e" + currentFactionName + "§c!");
        }
    }

    private void executeBalance(Player player, Faction faction) {
        player.sendMessage("§6[Казна] §eБаланс бюджета §b" + faction.getName() + "§e: §a" + faction.getDiamondBank() + " алмазов§e.");
    }

    private void executeDeposit(Player player, Faction faction, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f deposit <число>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§cУкажите корректное число алмазов!");
            return;
        }

        if (!hasEnoughDiamonds(player, amount)) {
            player.sendMessage("§cУ вас нет столько алмазов в инвентаре!");
            return;
        }

        removeDiamonds(player, amount);
        faction.deposit(amount);
        player.sendMessage("§aВы успешно внесли §b" + amount + " алмазов §aв казну!");
    }

    private void executeWithdraw(Player player, Faction faction, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cИспользование: /f withdraw <число>");
            return;
        }
        if (!faction.hasPermission(player.getUniqueId(), FactionPermission.WITHDRAW_MONEY)) {
            player.sendMessage("§cВаша должность не имеет права снимать средства из казны!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§cУкажите корректное число алмазов!");
            return;
        }

        if (faction.withdraw(amount)) {
            player.getInventory().addItem(new ItemStack(Material.DIAMOND, amount));
            player.sendMessage("§aВы успешно сняли §b" + amount + " алмазов §aиз казны.");
        } else {
            player.sendMessage("§cНедостаточно средств. Баланс казны: " + faction.getDiamondBank());
        }
    }

    private void executeRank(Player player, Faction faction, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cСправка по рангам: /f help");
            return;
        }
        String rankAction = args[1].toLowerCase();
        UUID playerUUID = player.getUniqueId();

        if (rankAction.equals("create") && args.length > 3) {
            if (!playerUUID.equals(faction.getLeader())) {
                player.sendMessage("§cТолько Верховный Лидер управляет созданием должностей!");
                return;
            }
            String deptName = args[2].toLowerCase();
            FactionDepartment dept = faction.getDepartments().get(deptName);
            if (dept == null) {
                player.sendMessage("§cВедомство не найдено.");
                return;
            }
            String rankName = args[3];
            if (dept.getRanks().containsKey(rankName.toLowerCase())) {
                player.sendMessage("§cДолжность уже существует.");
                return;
            }
            dept.addRank(new FactionRank(rankName));
            player.sendMessage("§aДобавлена должность §d" + rankName + " §aв ведомство §b" + dept.getName());
        }

        else if (rankAction.equals("remove") && args.length > 3) {
            if (!playerUUID.equals(faction.getLeader())) {
                player.sendMessage("§cТолько Верховный Лидер удаляет должности!");
                return;
            }
            String deptName = args[2].toLowerCase();
            FactionDepartment dept = faction.getDepartments().get(deptName);
            if (dept == null) return;

            String rankName = args[3].toLowerCase();
            if (deptName.equals("жители") && rankName.equals("крестьянин")) {
                player.sendMessage("§cНельзя удалить базовый ранг!");
                return;
            }
            dept.removeRank(rankName);
            player.sendMessage("§aДолжность удалена.");
        }

        else if (rankAction.equals("perm") && args.length > 5) {
            String permAction = args[2].toLowerCase();
            String deptName = args[3].toLowerCase();
            String rankName = args[4].toLowerCase();
            String rawPerm = args[5].toUpperCase();

            boolean isLeader = playerUUID.equals(faction.getLeader());
            boolean isDeptHead = faction.getDepartments().containsKey(deptName) &&
                    playerUUID.equals(faction.getDepartments().get(deptName).getDepartmentHead());

            if (!isLeader && !isDeptHead) {
                player.sendMessage("§cУ вас нет прав редактировать полномочия этого ведомства!");
                return;
            }

            FactionDepartment dept = faction.getDepartments().get(deptName);
            if (dept == null) return;
            FactionRank rank = dept.getRanks().get(rankName);
            if (rank == null) return;

            FactionPermission permission;
            try {
                permission = FactionPermission.valueOf(rawPerm);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cПраво не существует. См. /f help");
                return;
            }

            if (permAction.equals("add")) {
                rank.addPermission(permission);
                player.sendMessage("§aВыдано право " + permission.name());
            } else {
                rank.removePermission(permission);
                player.sendMessage("§cОтозвано право " + permission.name());
            }
        }
    }

    private void executeDept(Player player, Faction faction, String[] args) {
        String action = args[1].toLowerCase();
        UUID playerUUID = player.getUniqueId();

        if (action.equals("create") && args.length > 2) {
            if (!playerUUID.equals(faction.getLeader())) return;
            String name = args[2].toLowerCase();
            if (faction.getDepartments().containsKey(name)) return;

            faction.getDepartments().put(name, new FactionDepartment(args[2]));
            player.sendMessage("§aОсновано ведомство: §b" + args[2]);
        }
        else if (action.equals("remove") && args.length > 2) {
            if (!playerUUID.equals(faction.getLeader())) return;
            String name = args[2].toLowerCase();
            if (name.equals("жители")) return;

            faction.getDepartments().remove(name);
            player.sendMessage("§cВедомство упразднено.");
        }
        else if (action.equals("head") && args.length > 3) {
            if (!playerUUID.equals(faction.getLeader())) return;
            String deptName = args[2].toLowerCase();
            if (!faction.getDepartments().containsKey(deptName)) return;

            Player targetHead = Bukkit.getPlayer(args[3]);
            if (targetHead == null || !targetHead.isOnline()) return;

            faction.getDepartments().get(deptName).setDepartmentHead(targetHead.getUniqueId());
            faction.assignPlayer(targetHead.getUniqueId(), args[2], "Глава");
            player.sendMessage("§aНазначен новый Глава ведомства.");
        }
    }

    private void executeAssign(Player player, Faction faction, String currentFactionName, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cИспользование: /f assign <Ник> <Ведомство> <Ранг>");
            return;
        }
        String deptName = args[2].toLowerCase();
        String rankName = args[3];

        if (!faction.getDepartments().containsKey(deptName)) {
            player.sendMessage("§cТакого ведомства не существует!");
            return;
        }

        boolean isLeader = player.getUniqueId().equals(faction.getLeader());
        boolean isDeptHead = player.getUniqueId().equals(faction.getDepartments().get(deptName).getDepartmentHead());

        if (!isLeader && !isDeptHead) {
            player.sendMessage("§cВы не имеете права управлять кадрами здесь!");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) return;

        faction.assignPlayer(target.getUniqueId(), args[2], rankName);
        plugin.getPlayerFactionMap().put(target.getUniqueId(), currentFactionName);
        plugin.updateAllPlayersDisplay();

        player.sendMessage("§aКадры обновлены.");
    }

    private void executePassport(Player player, Faction faction, String currentFactionName, String[] args) {
        PassportManager passportManager = new PassportManager(plugin);

        if (args.length > 1) {
            if (!faction.hasPermission(player.getUniqueId(), FactionPermission.ISSUE_PASSPORT)) {
                player.sendMessage("§cВаша должность не уполномочена выписывать документы!");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) return;

            String targetFaction = plugin.getPlayerFactionMap().get(target.getUniqueId());
            String targetRole = "Скиталец";
            if (targetFaction != null) {
                targetRole = plugin.getFactions().get(targetFaction).getPlayerInfoString(target.getUniqueId())
                        .replaceAll("§[0-9a-fk-or]", "");
            }
            player.getInventory().addItem(passportManager.createPassport(target, targetFaction, targetRole));
            player.sendMessage("§aВы оформили паспорт гражданину.");
            return;
        }

        String role = faction.getPlayerInfoString(player.getUniqueId()).replaceAll("§[0-9a-fk-or]", "");
        player.getInventory().addItem(passportManager.createPassport(player, currentFactionName, role));
        player.sendMessage("§aВаш личный государственный паспорт успешно распечатан!");
    }

    private void executeAddEnemy(Player player, Faction faction, String currentFactionName, String[] args) {
        if (args.length < 2) return;
        if (!faction.hasPermission(player.getUniqueId(), FactionPermission.MANAGE_BLACK_LIST)) return;

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) return;

        if (faction.getMembers().contains(target.getUniqueId())) return;

        faction.addToBlacklist(target.getUniqueId());
        plugin.updateAllPlayersDisplay();

        player.sendMessage("§aИгрок внесён в ЧС.");
        target.sendMessage("§cГосударство §e" + currentFactionName + " §cобъявило вас врагом народа!");
    }

    // --- Дальнейшие вспомогательные методы (hasEnoughDiamonds, removeDiamonds, openFactionMenu, sendHelpMessage) остаются без изменений ---
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

    private void openFactionMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§0Государственный Аппарат");
        UUID uuid = player.getUniqueId();

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }

        ItemStack helpItem = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta helpMeta = helpItem.getItemMeta();
        if (helpMeta != null) {
            helpMeta.setDisplayName("§b❓ Справка по командам");
            List<String> lore = new ArrayList<>();
            lore.add("§7Нажмите, чтобы закрыть меню и");
            lore.add("§7посмотреть список команд в чате.");
            lore.add("");
            lore.add("§eИспользуйте: §f/f help");
            helpMeta.setLore(lore);
            helpItem.setItemMeta(helpMeta);
        }
        gui.setItem(8, helpItem);

        if (!plugin.getPlayerFactionMap().containsKey(uuid)) {
            ItemStack createBtn = new ItemStack(Material.WHITE_BANNER);
            ItemMeta meta = createBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a✨ Создать новое Государство");
                List<String> lore = new ArrayList<>();
                lore.add("§7Вы сейчас являетесь свободным скитальцем.");
                lore.add("§7Оснуйте свою фракцию и начните");
                lore.add("§7развивать политическую структуру!");
                lore.add("");
                lore.add("§eКоманда: §f/f create <Название>");
                meta.setLore(lore);
                createBtn.setItemMeta(meta);
            }
            gui.setItem(13, createBtn);
        } else {
            String factionName = plugin.getPlayerFactionMap().get(uuid);
            Faction faction = plugin.getFactions().get(factionName);

            ItemStack info = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = info.getItemMeta();
            if (infoMeta != null) {
                infoMeta.setDisplayName("§eГосударство: §6" + factionName);
                List<String> lore = new ArrayList<>();
                lore.add("§7Гражден в реестре: §a" + (faction.getMembers() != null ? faction.getMembers().size() : 1));
                lore.add("§7Верховный Лидер: §a" + Bukkit.getOfflinePlayer(faction.getLeader()).getName());
                lore.add("");
                lore.add("§7Ваш текущий статус:");
                lore.add(" " + faction.getPlayerInfoString(uuid));
                lore.add("");
                lore.add("§b👉 Напишите §f/f passport§b, чтобы получить");
                lore.add("§b   бумажный документ с этой информацией.");
                infoMeta.setLore(lore);
                info.setItemMeta(infoMeta);
            }
            gui.setItem(10, info);

            ItemStack bank = new ItemStack(Material.DIAMOND);
            ItemMeta bankMeta = bank.getItemMeta();
            if (bankMeta != null) {
                bankMeta.setDisplayName("§6💰 Nationale Казна");
                List<String> lore = new ArrayList<>();
                lore.add("§7Финансовые резервы in алмазах.");
                lore.add("");
                lore.add("§7Баланс бюджета: §a" + faction.getDiamondBank() + " 💎");
                lore.add("");
                lore.add("§7Действия в чате:");
                lore.add(" §e/f deposit <кол-во> §7— Пополнить казну");
                lore.add(" §e/f withdraw <кол-во> §7— Снять (нужны права)");
                bankMeta.setLore(lore);
                bank.setItemMeta(bankMeta);
            }
            gui.setItem(12, bank);

            ItemStack structure = new ItemStack(Material.COMPASS);
            ItemMeta structMeta = structure.getItemMeta();
            if (structMeta != null) {
                structMeta.setDisplayName("§b🏢 Министерства и Ведомства");
                List<String> lore = new ArrayList<>();
                lore.add("§7Активные категории гос. аппарата:");

                if (faction.getDepartments() != null && !faction.getDepartments().isEmpty()) {
                    for (String dept : faction.getDepartments().keySet()) {
                        FactionDepartment d = faction.getDepartments().get(dept);
                        if (d != null) {
                            String headName = d.getDepartmentHead() != null ?
                                    Bukkit.getOfflinePlayer(d.getDepartmentHead()).getName() : "Не назначен";
                            lore.add(" §7• §f" + d.getName() + " §7(Глава: §e" + headName + "§7)");
                        }
                    }
                } else {
                    lore.add(" §7• §cНет active ведомств");
                }

                lore.add("");
                lore.add("§7Управление (Для Лидеров):");
                lore.add(" §e/f dept create <Имя> §7— Открыть ведомство");
                lore.add(" §e/f dept head <Ведомство> <Ник> §7— Назначить министра");
                lore.add(" §e/f assign <Ник> <Ведомство> <Ранг> §7— Выдать должность");
                structMeta.setLore(lore);
                structure.setItemMeta(structMeta);
            }
            gui.setItem(14, structure);

            ItemStack diplo = new ItemStack(Material.RED_BANNER);
            ItemMeta diploMeta = diplo.getItemMeta();
            if (diploMeta != null) {
                diploMeta.setDisplayName("§c🛡️ Безопасность и Черный Список");
                List<String> lore = new ArrayList<>();
                lore.add("§7Игроки в ЧС подсвечиваются красным");
                lore.add("§7цветом на государственном уровне.");
                lore.add("");
                lore.add("§7Всего врагов народа: §c" + (faction.getBlacklist() != null ? faction.getBlacklist().size() : 0));
                lore.add("");
                lore.add("§7Управление:");
                lore.add(" §e/f addenemy <Ник> §7— Объявить врагом");
                diploMeta.setLore(lore);
                diplo.setItemMeta(diploMeta);
            }
            gui.setItem(16, diplo);
        }

        player.openInventory(gui);
    }

    private void sendHelpMessage(Player p) {
        p.sendMessage(" ");
        p.sendMessage("§6▬▬▬▬▬▬▬▬▬ §l[ СИСТЕМА СЕРВЕРА: ПОЛНАЯ СПРАВКА ] §6▬▬▬▬▬▬▬▬▬");
        p.sendMessage("§b👑 ВЕЛИКИЕ ГОСУДАРСТВА И ФРАКЦИИ:");
        p.sendMessage(" §e/f §7— Открыть графическое меню гос. аппарата");
        p.sendMessage(" §e/f list §7— Список всех государств на сервере");
        p.sendMessage(" §e/f info <Имя> §7— Посмотреть информацию о чужой стране");
        p.sendMessage(" §e/f leave §7— Эмигрировать (покинуть государство)");
        p.sendMessage(" §e/f disband §7— Полностью уничтожить свою фракцию (Лидер)");
        p.sendMessage(" §e/f create <Имя> §7— Основать новое независимое государство");
        p.sendMessage(" §e/f invite <Ник> §7— Пригласить жителя в государство");
        p.sendMessage(" §e/f accept §7— Принять активное приглашение");
        p.sendMessage(" §e/f kick <Ник> §7— Выгнать человека из фракции");
        p.sendMessage(" §e/f passport [Ник] §7— Распечатать бумажный паспорт гражданина");
        p.sendMessage(" ");
        p.sendMessage("§6💰 НАЦИОНАЛЬНЫЙ БАНК (КАЗНА):");
        p.sendMessage(" §e/f balance §7— Проверить баланс алмазов в казне");
        p.sendMessage(" §e/f deposit <число> §7— Сдать свои личные алмазы в гос. бюджет");
        p.sendMessage(" §e/f withdraw <число> §7— Снять алмазы из бюджета");
        p.sendMessage(" ");
        p.sendMessage("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}