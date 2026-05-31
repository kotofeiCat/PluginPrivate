package ru.politaboba.serverSystem.faction.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.model.Faction;
import ru.politaboba.serverSystem.faction.model.FactionDepartment;
import ru.politaboba.serverSystem.faction.model.FactionPermission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FactionTabCompleter implements TabCompleter {

    private final ServerSystem plugin;

    public FactionTabCompleter(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        List<String> completions = new ArrayList<>();

        // ==========================================
        // 1. ПЕРВЫЙ АРГУМЕНТ: /f [подкомманда]
        // ==========================================
        if (args.length == 1) {
            // Доступно абсолютно всем (включая Скитальцев)
            List<String> subCommands = new ArrayList<>(Arrays.asList("help", "create", "accept", "list", "info"));

            // Доступно только членам государств
            if (plugin.getPlayerFactionMap().containsKey(uuid)) {
                subCommands.addAll(Arrays.asList(
                        "balance", "deposit", "withdraw", "dept", "rank",
                        "assign", "passport", "addenemy", "chat", "invite", "kick", "leave", "disband", "color"
                ));
            }

            if (player.isOp()) {
                subCommands.add("cookbook");
            }

            return filter(subCommands, args[0]);
        }

        // Для всех последующих аргументов (кроме глобальных /f info) нужна проверка фракции игрока
        String currentFactionName = plugin.getPlayerFactionMap().get(uuid);

        // Исключение: Команда /f info <Название> должна подсказывать чужие фракции Скитальцу
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return filter(new ArrayList<>(plugin.getFactions().keySet()), args[1]);
        }

        if (currentFactionName == null) return completions;
        Faction faction = plugin.getFactions().get(currentFactionName);
        if (faction == null) return completions;

        boolean isLeader = uuid.equals(faction.getLeader());
        String root = args[0].toLowerCase();

        // ==========================================
        // 2. ВТОРЫЕ АРГУМЕНТЫ: /f <команда> [аргумент]
        // ==========================================
        if (args.length == 2) {
            if (root.equals("dept")) {
                List<String> sub = new ArrayList<>(Arrays.asList("create", "head"));
                if (isLeader) sub.add("remove");
                return filter(sub, args[1]);
            }
            if (root.equals("rank")) {
                List<String> sub = new ArrayList<>(Arrays.asList("create", "perm"));
                if (isLeader) sub.add("remove");
                return filter(sub, args[1]);
            }
            if (root.equals("color") && isLeader) {
                return filter(Arrays.asList("RED", "GREEN", "AQUA", "YELLOW", "LIGHT_PURPLE", "GOLD", "GRAY", "BLUE"), args[1]);
            }
            // Если это команды работы с игроками — возвращаем null (Spigot подставит онлайн игроков сервера)
            if (Arrays.asList("kick", "invite", "passport", "addenemy", "assign").contains(root)) {
                return null;
            }
        }

        // ==========================================
        // 3. ТРЕТЬИ АРГУМЕНТЫ
        // ==========================================
        if (args.length == 3) {
            String sub = args[1].toLowerCase();

            // /f rank perm [add/remove]
            if (root.equals("rank") && sub.equals("perm")) {
                return filter(Arrays.asList("add", "remove"), args[2]);
            }
            // /f rank create [Ведомство] или /f rank remove [Ведомство]
            if (root.equals("rank") && (sub.equals("create") || sub.equals("remove"))) {
                return filter(new ArrayList<>(faction.getDepartments().keySet()), args[2]);
            }
            // /f dept head [Ведомство] или /f dept remove [Ведомство]
            if (root.equals("dept") && (sub.equals("head") || sub.equals("remove"))) {
                return filter(new ArrayList<>(faction.getDepartments().keySet()), args[2]);
            }
            // /f assign <Ник> [Ведомство]
            if (root.equals("assign")) {
                return filter(new ArrayList<>(faction.getDepartments().keySet()), args[2]);
            }
        }

        // ==========================================
        // 4. ЧЕТВЕРТЫЕ АРГУМЕНТЫ
        // ==========================================
        if (args.length == 4) {
            String sub = args[1].toLowerCase();

            // /f rank perm <add/remove> [Ведомство]
            if (root.equals("rank") && sub.equals("perm")) {
                return filter(new ArrayList<>(faction.getDepartments().keySet()), args[3]);
            }
            // /f rank remove <Ведомство> [Должность]
            if (root.equals("rank") && sub.equals("remove")) {
                String deptName = args[2].toLowerCase();
                FactionDepartment dept = faction.getDepartments().get(deptName);
                if (dept != null) {
                    return filter(new ArrayList<>(dept.getRanks().keySet()), args[3]);
                }
            }
            // /f assign <Ник> <Ведомство> [Ранг] -> ИСПРАВЛЕН ИНДЕКС
            if (root.equals("assign")) {
                String deptName = args[2].toLowerCase();
                FactionDepartment dept = faction.getDepartments().get(deptName);
                if (dept != null) {
                    return filter(new ArrayList<>(dept.getRanks().keySet()), args[3]);
                }
            }
            // /f dept head <Ведомство> [Ник]
            if (root.equals("dept") && sub.equals("head")) {
                return null; // Список игроков
            }
        }

        // ==========================================
        // 5. ПЯТЫЕ АРГУМЕНТЫ
        // ==========================================
        if (args.length == 5) {
            String sub = args[1].toLowerCase();

            // /f rank perm <add/remove> <Ведомство> [Должность] -> ИСПРАВЛЕН ИНДЕКС
            if (root.equals("rank") && sub.equals("perm")) {
                String deptName = args[3].toLowerCase();
                FactionDepartment dept = faction.getDepartments().get(deptName);
                if (dept != null) {
                    return filter(new ArrayList<>(dept.getRanks().keySet()), args[4]);
                }
            }
        }

        // ==========================================
        // 6. ШЕСТЫЕ АРГУМЕНТЫ
        // ==========================================
        if (args.length == 6) {
            String sub = args[1].toLowerCase();

            // /f rank perm <add/remove> <Ведомство> <Должность> [Право] -> ИСПРАВЛЕН ИНДЕКС
            if (root.equals("rank") && sub.equals("perm")) {
                List<String> permissions = Arrays.stream(FactionPermission.values())
                        .map(Enum::name)
                        .collect(Collectors.toList());
                return filter(permissions, args[5]);
            }
        }

        return completions;
    }

    private List<String> filter(List<String> list, String latestArg) {
        String lower = latestArg.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}