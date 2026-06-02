package ru.politaboba.serverSystem.Dangeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class DungeonDevTool implements Listener, CommandExecutor {

    // Храним точку Origin (0,0,0) для каждого админа
    private final HashMap<UUID, Location> playerOrigins = new HashMap<>();

    // Храним первую точку для выделения региона (Lobby Min)
    private final HashMap<UUID, Location> playerPos1 = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("dungeon.admin")) return true;

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§c[Dev] Посмотрите на блок-основание (Origin) замка!");
            return true;
        }

        playerOrigins.put(player.getUniqueId(), target.getLocation());
        player.sendMessage("§a[Dev] Точка отсчета (Origin) зафиксирована на: "
                + target.getX() + ", " + target.getY() + ", " + target.getZ());
        player.sendMessage("§eИнструкция для Палочки Ифрита (Blaze Rod):");
        player.sendMessage(" §6• ЛКМ §7— Задать ТОЧКУ 1 (Min) для области лобби");
        player.sendMessage(" §6• Shift + ПКМ §7— Задать ТОЧКУ 2 (Max) и получить код области");
        player.sendMessage(" §6• Обычный ПКМ §7— Получить отступ одиночного блока (двери, сундуки)");
        return true;
    }

    @EventHandler
    public void onDevInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeon.admin")) return;
        if (event.getItem() == null || event.getItem().getType() != Material.BLAZE_ROD) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Отменяем ломание блоков / открытие сундуков палочкой
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        if (!playerOrigins.containsKey(uuid)) {
            player.sendMessage("§c[Dev] Сначала задайте точку отсчета структуры: /dungeonorigin");
            return;
        }

        Location origin = playerOrigins.get(uuid);

        // --- ЛОГИКА 1: ЛКМ — Выделение ТОЧКИ 1 (Регион) ---
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            playerPos1.put(uuid, clickedBlock.getLocation());
            player.sendMessage("§a[Dev] Точка 1 (MIN) для области лобби установлена.");
            return;
        }

        // --- ЛОГИКА 2: Shift + ПКМ — Выделение ТОЧКИ 2 и вывод ОБЛАСТИ ---
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            if (!playerPos1.containsKey(uuid)) {
                player.sendMessage("§c[Dev] Сначала кликните ЛКМ, чтобы задать Точку 1!");
                return;
            }

            Location pos1 = playerPos1.get(uuid);
            Location pos2 = clickedBlock.getLocation();

            // Автоматически вычисляем правильный min и max (как в WorldEdit),
            // чтобы админ мог кликать по углам в любом порядке
            int minX = Math.min(pos1.getBlockX(), pos2.getBlockX()) - origin.getBlockX();
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY()) - origin.getBlockY();
            int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ()) - origin.getBlockZ();

            int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) - origin.getBlockX();
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) - origin.getBlockY();
            int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) - origin.getBlockZ();

            player.sendMessage("§7--- §6Готовый YAML-код для lobby-zone: §7---");
            player.sendMessage("§a  lobby-zone:");
            player.sendMessage("§a    min:");
            player.sendMessage(String.format("§a      x: %d\n§a      y: %d\n§a      z: %d", minX, minY, minZ));
            player.sendMessage("§a    max:");
            player.sendMessage(String.format("§a      x: %d\n§a      y: %d\n§a      z: %d", maxX, maxY, maxZ));
            return;
        }

        // --- ЛОГИКА 3: Обычный ПКМ — Одиночные блоки / Элементы списков ---
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
            int offsetX = clickedBlock.getX() - origin.getBlockX();
            int offsetY = clickedBlock.getY() - origin.getBlockY();
            int offsetZ = clickedBlock.getZ() - origin.getBlockZ();

            player.sendMessage("§7[Блок: " + clickedBlock.getType().name() + "] §eОтступы от origin:");
            player.sendMessage("§bДля одиночного значения (stage1-spawn / boss-spawn):");
            player.sendMessage(String.format("  §a%s", String.format("x: %d, y: %d, z: %d", offsetX, offsetY, offsetZ)));
            player.sendMessage("§bДля списков (wave-spawns / doors-stage1 / loot-chests):");
            player.sendMessage(String.format("  §a%s", String.format("- {x: %d, y: %d, z: %d}", offsetX, offsetY, offsetZ)));
        }
    }
}