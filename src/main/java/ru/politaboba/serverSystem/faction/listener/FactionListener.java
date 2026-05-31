package ru.politaboba.serverSystem.faction.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // ЕСЛИ ЭТО НЕ НАШИ МЕНЮ — СРАЗУ ИГНОРИРУЕМ И ДАЕМ РАБОТАТЬ ДРУГИМ ПЛАГИНАМ (В ТОМ ЧИСЛЕ BOUNTY)
        if (!title.equals("§0Государственный Аппарат") &&
                !title.equals("§0Меню Фракции") &&
                !title.equals("§0Управление Дипломатией")) {
            return;
        }

        // Блокируем кражу предметов строго в меню фракции
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        UUID playerUUID = player.getUniqueId();
        String currentFaction = plugin.getPlayerFactionMap().get(playerUUID);

        // --- ЛОГИКА ОБНОВЛЕННОГО ИНТЕРАКТИВНОГО МЕНЮ ---
        if (title.equals("§0Государственный Аппарат")) {
            if (clickedItem.getType() == Material.KNOWLEDGE_BOOK) {
                player.closeInventory();
                player.performCommand("f help");
            }
            else if (clickedItem.getType() == Material.WHITE_BANNER) {
                player.closeInventory();
                player.sendMessage("§e[Фракции] Введите в чат: §6/f create <название>");
            }
            else if (clickedItem.getType() == Material.RED_BANNER) {
                player.closeInventory();
                openDiplomacyMenu(player);
            }
        }
        // --- ЛОГИКА МЕНЮ ДИПЛОМАТИИ ---
        else if (title.equals("§0Управление Дипломатией")) {
            if (clickedItem.getType() == Material.WRITABLE_BOOK) {
                player.closeInventory();

                Faction faction = plugin.getFactions().get(currentFaction);
                if (faction == null) return;

                if (!faction.getLeader().equals(playerUUID)) {
                    player.sendMessage("§cТолько лидер фракции может изменять ЧС!");
                    return;
                }

                player.sendMessage("§e[Фракции] Чтобы добавить врага, введите: §6/f addenemy <ник>");
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