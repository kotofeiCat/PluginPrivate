package ru.politaboba.serverSystem.faction.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MsgTabCompleter implements TabCompleter {

    private final ServerSystem plugin;

    public MsgTabCompleter(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Подсказки нужны только если вводится первый аргумент (ник получателя): /msg [Ник]
        if (args.length == 1) {
            String lastArg = args[0].toLowerCase();

            // Собираем ники всех онлайн-игроков, кроме самого отправителя
            List<String> playerNames = plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> !p.getName().equals(sender.getName()))
                    .map(Player::getName)
                    .collect(Collectors.toList());

            // Фильтруем по начальным буквам, которые уже ввел админ/игрок
            return playerNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(lastArg))
                    .collect(Collectors.toList());
        }

        // Для второго аргумента и далее (текст сообщения) возвращаем пустой список,
        // чтобы Майнкрафт не подсовывал лишних подсказок во время написания текста
        return new ArrayList<>();
    }
}