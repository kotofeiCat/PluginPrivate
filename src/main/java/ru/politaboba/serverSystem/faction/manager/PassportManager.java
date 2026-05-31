package ru.politaboba.serverSystem.faction.manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class PassportManager {

    private final ServerSystem plugin;

    public PassportManager(ServerSystem plugin) {
        this.plugin = plugin;
    }

    public ItemStack createPassport(Player target, String factionName, String role) {
        ItemStack passport = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) passport.getItemMeta();

        if (meta != null) {
            meta.setTitle("§0Паспорт: §l" + target.getName());
            meta.setAuthor("§6§lКАНЦЕЛЯРИЯ");

            List<String> pages = new ArrayList<>();
            String pageContent =
                    "§0§lГОСУДАРСТВЕННЫЙ\n" +
                            "§0§l     ПАСПОРТ\n\n" +
                            "§8Имя гражданина:\n" +
                            "§0" + target.getName() + "\n\n" +
                            "§8Принадлежность:\n" +
                            "§1" + (factionName != null ? factionName : "Скиталец") + "\n\n" +
                            "§8Должность/Ранг:\n" +
                            "§d" + role + "\n\n" +
                            "§8§oВыдано официально сервером.";

            pages.add(pageContent);
            meta.setPages(pages);

            List<String> lore = new ArrayList<>();
            lore.add("§7Официальный документ удостоверения личности.");
            lore.add("§7Владелец: §e" + target.getName());
            meta.setLore(lore);

            passport.setItemMeta(meta);
        }

        return passport;
    }
}