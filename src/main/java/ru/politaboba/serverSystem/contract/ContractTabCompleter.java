package ru.politaboba.serverSystem.contract;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ContractTabCompleter implements TabCompleter {

    private final ServerSystem plugin;

    public ContractTabCompleter(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            return filter(Arrays.asList("list", "send", "terminate", "review"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("send")) {
                return null;
            }
            if (args[0].equalsIgnoreCase("terminate")) {
                List<String> activeIds = plugin.getAgreements().stream()
                        .map(Agreement::getId)
                        .collect(Collectors.toList());
                return filter(activeIds, args[1]);
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