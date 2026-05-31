package ru.politaboba.serverSystem.item.food;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.List;

public class FoodFactory {

    private final ServerSystem plugin;

    public FoodFactory(ServerSystem plugin) {
        this.plugin = plugin;
    }

    public void registerAllCooking() {
        registerIngredients();
    }

    // ==========================================
    // 🌾 СОЗДАНИЕ СЫРЫХ ЗАГОТОВОК В ВЕРСТАКЕ
    // ==========================================
    private void registerIngredients() {
        // 1. Сырой стейк Спринтер (заготовка для костра)
        ItemStack rawSprinter = createIngredient("rp_raw_sprinter", Material.BEEF, "§dСырой Стейк «Спринтер»",
                List.of("§7Мясо замариновано в огненных специях.", "§cТребует идеальной прожарки на КОСТРЕ!"));
        registerShapeless("recipe_raw_sprinter", rawSprinter, Material.BEEF, Material.BROWN_MUSHROOM, Material.BLAZE_POWDER);

        // 2. Сырой Шашлык (заготовка для костра)
        ItemStack rawKebab = createIngredient("rp_raw_kebab", Material.STONE_BUTTON, "§6Сырой Шашлык на Шампуре",
                List.of("§7Сырое мясо, насаженное на палку.", "§cТребует идеальной прожарки на КОСТРЕ!"));
        // Используем каменную кнопку как текстуру шампура с мясом, либо оставь STICK/BEEF
        registerShapeless("recipe_raw_kebab", rawKebab, Material.STICK, Material.BEEF, Material.BONE_MEAL);

        // 3. Сырой Карп (заготовка для костра)
        ItemStack rawCarp = createIngredient("rp_raw_carp", Material.COD, "§bСырой Подготовленный Карп",
                List.of("§7Рыба очищена и вымочена в сиропе.", "§cТребует идеальной прожарки на КОСТРЕ!"));
        registerShapeless("recipe_raw_carp", rawCarp, Material.COD, Material.SHORT_GRASS, Material.HONEY_BOTTLE);

        // 4. Мятная заварка (ингредиент для зельеварки)
        // ИСПРАВЛЕНО: Меняем Material.SHORT_GRASS на Material.FERMENTED_SPIDER_EYE
        ItemStack mintBlend = createIngredient("rp_mint_blend", Material.FERMENTED_SPIDER_EYE, "§aМятная Смесь для Заварки",
                List.of("§7Измельченные листья дикой мяты и сахар.", "§eИспользуется для варки чая в ЗЕЛЬЕВАРКЕ."));
        // Рецепт крафта в верстаке остается прежним и удобным для игроков!
        registerShapeless("recipe_mint_blend", mintBlend, Material.SHORT_GRASS, Material.SUGAR);
    }

    // Утилитарные методы создания предметов
    private ItemStack createIngredient(String nbtId, Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, nbtId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void registerShapeless(String keyName, ItemStack result, Material... ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        if (Bukkit.getRecipe(key) != null) return;
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material mat : ingredients) {
            recipe.addIngredient(mat);
        }
        Bukkit.addRecipe(recipe);
    }

    public static ItemStack createBeerWort(ServerSystem plugin) {
        ItemStack wort = new ItemStack(Material.POTION);
        ItemMeta meta = wort.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Сырое Пивное Сусло");
            meta.setLore(List.of(
                    "§7Густой сладковатый концентрат злаков.",
                    "§7Получен путем варки в котле.",
                    "",
                    "§cНепригодно для питья! Требует",
                    "§cсозревания в Бочке Брожения."
            ));
            NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "beer_wort");
            wort.setItemMeta(meta);
        }
        return wort;
    }
}