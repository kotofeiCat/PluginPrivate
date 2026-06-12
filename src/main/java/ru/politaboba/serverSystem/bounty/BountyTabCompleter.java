package ru.politaboba.serverSystem.bounty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BountyTabCompleter implements TabCompleter {

    private final ServerSystem plugin;

    public BountyTabCompleter(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("target", "board");
            return filter(subCommands, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("target")) {
                return null;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("target")) {
                List<String> suggestions = Arrays.asList("5", "10", "16", "32", "64");
                return filter(suggestions, args[2]);
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