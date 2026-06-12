package ru.politaboba.serverSystem.cases;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.arrest.ArrestManager;
import ru.politaboba.serverSystem.item.ItemFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class CaseManager {

    private final ServerSystem plugin;
    private final Random random = new Random();
    private final Map<String, List<LootItem>> caseLootTables = new HashMap<>();
    private final Map<String, Integer> caseCosts = new HashMap<>();

    public CaseManager(ServerSystem plugin) {
        this.plugin = plugin;
        createTable();
        setupCases();
    }

    private void createTable() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS ss_player_donate (uuid VARCHAR(36) PRIMARY KEY, coins INT DEFAULT 0)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Кейсы] Не удалось создать таблицу донат-валюты!");
            e.printStackTrace();
        }
    }

    private void setupCases() {
        // --- 1. ОБЫЧНЫЙ КЕЙС (25 коинов) ---
        caseCosts.put("common_case", 25);
        List<LootItem> commonLoot = new ArrayList<>();
        // Мусор
        commonLoot.add(new LootItem("coal", 40));
        commonLoot.add(new LootItem("iron_ingot", 30));
        // Кастомная еда
        commonLoot.add(new LootItem("sprinter_steak", 15));
        commonLoot.add(new LootItem("kebab", 15));
        commonLoot.add(new LootItem("royal_carp", 15));
        // Маленький шанс на ценные ресурсы и алебарду
        commonLoot.add(new LootItem("diamond", 3));
        commonLoot.add(new LootItem("netherite_scrap", 1));
        commonLoot.add(new LootItem("rp_halberd", 1));
        caseLootTables.put("common_case", commonLoot);

        // --- 2. РЕДКИЙ КЕЙС (50 коинов) ---
        caseCosts.put("rare_case", 50);
        List<LootItem> rareLoot = new ArrayList<>();
        // Те же предметы, но с повышенным шансом
        rareLoot.add(new LootItem("coal", 10));
        rareLoot.add(new LootItem("iron_ingot", 15));
        rareLoot.add(new LootItem("sprinter_steak", 25));
        rareLoot.add(new LootItem("kebab", 25));
        rareLoot.add(new LootItem("royal_carp", 25));
        rareLoot.add(new LootItem("diamond", 15));
        rareLoot.add(new LootItem("netherite_scrap", 10));
        rareLoot.add(new LootItem("rp_halberd", 6));
        // Уникальный дроп редкого кейса: чистый незерит или книги зачарования IV
        rareLoot.add(new LootItem("netherite_ingot", 4));
        rareLoot.add(new LootItem("rare_enchant_book", 10));
        caseLootTables.put("rare_case", rareLoot);

        // --- 3. ЭПИЧЕСКИЙ КЕЙС (100 коинов) ---
        caseCosts.put("epic_case", 100);
        List<LootItem> epicLoot = new ArrayList<>();
        // Ценные ресурсы
        epicLoot.add(new LootItem("diamond_block", 15));
        epicLoot.add(new LootItem("netherite_ingot", 15));
        // Магические книги VI уровня
        epicLoot.add(new LootItem("book_protection_6", 15));
        epicLoot.add(new LootItem("book_sharpness_6", 15));
        epicLoot.add(new LootItem("book_power_6", 15));
        // Топовое эпическое оружие
        epicLoot.add(new LootItem("rp_storm_bow", 8));
        epicLoot.add(new LootItem("artifact_axe", 7));
        caseLootTables.put("epic_case", epicLoot);

        // --- 4. ЛЕГЕНДАРНЫЙ КЕЙС (300 коинов) ---
        caseCosts.put("legendary_case", 300);
        List<LootItem> legendaryLoot = new ArrayList<>();
        // Оружие высшего тира с равными шансами
        legendaryLoot.add(new LootItem("rp_zephyr_spear", 25));
        legendaryLoot.add(new LootItem("magnetic_greatsword", 25));
        legendaryLoot.add(new LootItem("rp_storm_bow", 25));
        legendaryLoot.add(new LootItem("artifact_axe", 25));
        // Низкий шанс на шаблон брони Ловца Ветров
        legendaryLoot.add(new LootItem("wind_catcher_template", 5));
        caseLootTables.put("legendary_case", legendaryLoot);
    }

    public int getCoins(UUID uuid) {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("SELECT coins FROM ss_player_donate WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("coins");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void addCoins(UUID uuid, int amount) {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ss_player_donate (uuid, coins) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET coins = coins + ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, amount);
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean removeCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        if (current < amount) return false;

        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE ss_player_donate SET coins = coins - ? WHERE uuid = ?")) {
            ps.setInt(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getCaseCost(String caseId) {
        return caseCosts.getOrDefault(caseId, 0);
    }

    public ItemStack rollLoot(String caseId) {
        List<LootItem> lootTable = caseLootTables.get(caseId);
        if (lootTable == null) return null;

        int totalWeight = lootTable.stream().mapToInt(LootItem::getWeight).sum();
        int rolledValue = random.nextInt(totalWeight);

        int currentSum = 0;
        for (LootItem item : lootTable) {
            currentSum += item.getWeight();
            if (rolledValue < currentSum) {
                return createActualItemStack(item.getItemId());
            }
        }
        return new ItemStack(Material.COAL);
    }

    private ItemStack createActualItemStack(String itemId) {
        // Проверяем кастомное снаряжение (Оружие/Шаблоны)
        if (plugin.getEquipmentManager().getEquipmentById(itemId) != null) {
            return plugin.getEquipmentManager().getEquipmentById(itemId).createItemStack(plugin);
        }
        // Проверяем кулинарию и алкоголь
        if (plugin.getFoodManager().getDishById(itemId) != null) {
            return plugin.getFoodManager().getDishById(itemId).createCookedItem(plugin);
        }
        // Ручные фабричные предметы
        if (itemId.equals("rp_smoke_bomb")) {
            return new ItemFactory(plugin).createSmokeBomb();
        }
        if (itemId.equals("rp_handcuffs")) {
            return ArrestManager.createHandcuffs();
        }

        // Генерация кастомных высокоуровневых книг зачарования
        if (itemId.equals("rare_enchant_book")) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                org.bukkit.enchantments.Enchantment unbreaking = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                if (unbreaking != null) meta.addStoredEnchant(unbreaking, 4, true);
                meta.displayName(net.kyori.adventure.text.Component.text("Редкая книга: Прочность IV", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                book.setItemMeta(meta);
            }
            return book;
        }
        if (itemId.equals("book_protection_6")) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                org.bukkit.enchantments.Enchantment prot = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
                if (prot != null) meta.addStoredEnchant(prot, 6, true);
                meta.displayName(net.kyori.adventure.text.Component.text("Забытый фолиант: Защита VI", net.kyori.adventure.text.format.NamedTextColor.AQUA));
                book.setItemMeta(meta);
            }
            return book;
        }
        if (itemId.equals("book_sharpness_6")) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                org.bukkit.enchantments.Enchantment sharp = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
                if (sharp != null) meta.addStoredEnchant(sharp, 6, true);
                meta.displayName(net.kyori.adventure.text.Component.text("Древний манускрипт: Острота VI", net.kyori.adventure.text.format.NamedTextColor.RED));
                book.setItemMeta(meta);
            }
            return book;
        }
        if (itemId.equals("book_power_6")) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                org.bukkit.enchantments.Enchantment power = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
                if (power != null) meta.addStoredEnchant(power, 6, true);
                meta.displayName(net.kyori.adventure.text.Component.text("Эльфийские писания: Сила VI", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                book.setItemMeta(meta);
            }
            return book;
        }

        // Автоматическая обработка ванильных материалов (мусор/ресурсы)
        try {
            Material mat = Material.valueOf(itemId.toUpperCase());
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Кейсы] Не удалось определить предмет с ID: " + itemId);
        }

        return new ItemStack(Material.PAPER);
    }

    public static class LootItem {
        private final String itemId;
        private final int weight;

        public LootItem(String itemId, int weight) {
            this.itemId = itemId;
            this.weight = weight;
        }

        public String getItemId() { return itemId; }
        public int getWeight() { return weight; }
    }
}