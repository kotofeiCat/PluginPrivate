package ru.politaboba.serverSystem.faction.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.model.Faction;

import java.util.UUID;

public class FactionListener implements Listener {

    private final ServerSystem plugin;

    public FactionListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.updateAllPlayersDisplay();
    }

    // НОВАЯ ИНТЕРАКТИВНАЯ ВИЗУАЛЬНАЯ RP МЕХАНИКА ПАСПОРТОВ
    @EventHandler
    public void onPlayerShowPassport(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player clicker = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        ItemStack item = clicker.getInventory().getItemInMainHand();

        if (item.getType() == Material.WRITTEN_BOOK && item.hasItemMeta()) {
            BookMeta meta = (BookMeta) item.getItemMeta();

            // Проверяем, что это действительно легитимный паспорт от Канцелярии сервера
            if (meta.getTitle() != null && meta.getTitle().contains("Паспорт:") && "§6§lКАНЦЕЛЯРИЯ".equals(meta.getAuthor())) {
                event.setCancelled(true); // Отменяем стандартное прочтение книги у самого себя

                // Посылаем атмосферные RP-сообщения
                clicker.sendMessage("§a[РП] Вы уверенно предъявили свое удостоверение личности гражданину §e" + target.getName() + "§a.");
                target.sendMessage("§a[РП] Игрок §e" + clicker.getName() + " §aпредъявил вам свой государственный паспорт!");

                // Физически открываем паспорт на экране целевого игрока
                target.openBook(item);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!title.equals("§0Государственный Аппарат") &&
                !title.equals("§0Меню Фракции") &&
                !title.equals("§0Управление Дипломатией")) {
            return;
        }

        event.setCancelled(true); // Защита предметов от кражи в меню

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        UUID playerUUID = player.getUniqueId();
        String currentFaction = plugin.getPlayerFactionMap().get(playerUUID);

        if (title.equals("§0Государственный Аппарат")) {
            if (clickedItem.getType() == Material.KNOWLEDGE_BOOK) {
                player.closeInventory();
                player.performCommand("f help");
            }
            else if (clickedItem.getType() == Material.WHITE_BANNER) {
                player.closeInventory();
                player.sendMessage("§e[Фракции] Введите команду: §6/f create <название>");
            }
            else if (clickedItem.getType() == Material.RED_BANNER) {
                player.closeInventory();
                openDiplomacyMenu(player);
            }
        }
        else if (title.equals("§0Управление Дипломатией")) {
            if (clickedItem.getType() == Material.WRITABLE_BOOK) {
                player.closeInventory();

                Faction faction = plugin.getFactions().get(currentFaction);
                if (faction == null) return;

                if (!faction.getLeader().equals(playerUUID)) {
                    player.sendMessage("§cТолько лидер фракции может изменять ЧС!");
                    return;
                }
                player.sendMessage("§e[Фракции] Дабы объявить врага, введите: §6/f addenemy <ник>");
            }
        }
    }

    private void openDiplomacyMenu(Player player) {
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, "§0Управление Дипломатией");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }

        ItemStack addEnemy = new ItemStack(Material.WRITABLE_BOOK);
        org.bukkit.inventory.meta.ItemMeta meta = addEnemy.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cДобавить игрока в ЧС");
            addEnemy.setItemMeta(meta);
        }

        gui.setItem(13, addEnemy);
        player.openInventory(gui);
    }
}