package ru.politaboba.serverSystem.cases;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CaseTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("serversystem.admin")) {
            completions.add("givecoins");
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givecoins")) {
            return null; // Возвращает список игроков онлайн
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givecoins")) {
            completions.add("50");
            completions.add("100");
            completions.add("500");
            return completions;
        }

        return Collections.emptyList();
    }
}