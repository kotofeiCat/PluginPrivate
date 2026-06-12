package ru.politaboba.serverSystem.item.food;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.item.food.model.CustomDish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoodManager {

    private final ServerSystem plugin;
    private final Map<String, CustomDish> dishesById = new HashMap<>();
    private final Map<String, CustomDish> dishesByRawId = new HashMap<>();

    public FoodManager(ServerSystem plugin) {
        this.plugin = plugin;
        registerAllDishes();
    }

    public CustomDish getDishById(String id) { return dishesById.get(id); }
    public CustomDish getDishByRawId(String rawId) { return dishesByRawId.get(rawId); }

    private void registerDish(CustomDish dish) {
        dishesById.put(dish.getId(), dish);
        if (dish.getRawId() != null) {
            dishesByRawId.put(dish.getRawId(), dish);
        }
    }

    public void registerAllCooking() {
        // Старые рецепты
        CustomDish sprinter = getDishById("sprinter_steak");
        if (sprinter != null) {
            registerShapeless("recipe_raw_sprinter", sprinter.createRawItem(plugin),
                    Material.BEEF, Material.BROWN_MUSHROOM, Material.BLAZE_POWDER);
        }

        CustomDish kebab = getDishById("kebab");
        if (kebab != null) {
            registerShapeless("recipe_raw_kebab", kebab.createRawItem(plugin),
                    Material.STICK, Material.BEEF, Material.BONE_MEAL);
        }

        CustomDish carp = getDishById("royal_carp");
        if (carp != null) {
            registerShapeless("recipe_raw_carp", carp.createRawItem(plugin),
                    Material.COD, Material.SHORT_GRASS, Material.HONEY_BOTTLE);
        }

        ItemStack mintBlend = createIngredient("rp_mint_blend", Material.FERMENTED_SPIDER_EYE, "§aМятная Смесь для Заварки",
                List.of("§7Измельченные листья дикой мяты и сахар.", "§eИспользуется для варки чая в ЗЕЛЬЕВАРКЕ."));
        registerShapeless("recipe_mint_blend", mintBlend, Material.SHORT_GRASS, Material.SUGAR);

        // ==========================================
        // НОВЫЕ РЕЦЕПТЫ ВЕРСТАКА
        // ==========================================

        // 1. Армейский Сухпаёк (Заготовка)
        CustomDish soldierBread = getDishById("soldier_bread");
        if (soldierBread != null) {
            registerShapeless("recipe_raw_soldier_bread", soldierBread.createRawItem(plugin),
                    Material.BREAD, Material.BONE_MEAL);
        }

        // 2. Картофель «По-разбойничьи» (Заготовка)
        CustomDish ashPotato = getDishById("ash_potato");
        if (ashPotato != null) {
            registerShapeless("recipe_raw_ash_potato", ashPotato.createRawItem(plugin),
                    Material.POTATO, Material.GUNPOWDER, Material.PAPER);
        }

        // 3. Заварка: Огуречный Лимонад (ингредиент для зельеварки)
        ItemStack cucumberBlend = createIngredient("rp_cucumber_blend", Material.SPIDER_EYE, "§bОсвежающий Набор для Лимонада",
                List.of("§7Свежая лоза, сахар и семена.", "§eИспользуется для варки в ЗЕЛЬЕВАРКЕ."));
        registerShapeless("recipe_cucumber_blend", cucumberBlend, Material.MELON_SEEDS, Material.SUGAR, Material.VINE);

        // 4. Заварка: Хвойный Отвар (ингредиент для зельеварки)
        ItemStack pineGather = createIngredient("rp_pine_gather", Material.PUMPKIN_SEEDS, "§2Хвойный Лечебный Сбор",
                List.of("§7Душистая хвоя и сахар для дезинфекции.", "§eИспользуется для варки в ЗЕЛЬЕВАРКЕ."));
        registerShapeless("recipe_pine_gather", pineGather, Material.SPRUCE_LEAVES, Material.SUGAR);
    }

    private void registerShapeless(String keyName, ItemStack result, Material... ingredients) {
        if (result == null) return;
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        if (Bukkit.getRecipe(key) != null) return;
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material mat : ingredients) {
            recipe.addIngredient(mat);
        }
        Bukkit.addRecipe(recipe);
    }

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

    private void registerAllDishes() {
        // Старый брак
        registerDish(new CustomDish.Builder("raw_ruined", Material.ROTTEN_FLESH, "§cНедожаренный кусок пищи")
                .lore(List.of("§7Результат неудачной кулинарии. Побочные эффекты."))
                .effect(new PotionEffect(PotionEffectType.POISON, 160, 0))
                .effect(new PotionEffect(PotionEffectType.HUNGER, 300, 1))
                .consumeMessage("§c§oВы съели недожаренную склизкую пищу... Живот сильно скрутило.")
                .build());

        registerDish(new CustomDish.Builder("burnt_ruined", Material.CHARCOAL, "§8Сгоревшие угольные остатки")
                .lore(List.of("§7Результат неудачной кулинарии. Побочные эффекты."))
                .effect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0))
                .consumeMessage("§8§oВы попытались разжевать чистый уголь. Во рту остался мерзкий привкус гари.")
                .build());

        // Старые блюда
        registerDish(new CustomDish.Builder("mint_tea", Material.POTION, "§aЦелебный Мятный Чай")
                .lore(List.of("§7Ароматный травяной отвар.", "", "§⚡ Эффект (10 минут):", " §e• Регенерация I"))
                .effect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 0))
                .consumeMessage("§a⚡ Вы выпили Целебный мятный чай. Силы плавно возвращаются к вам.")
                .build());

        registerDish(new CustomDish.Builder("sprinter_steak", Material.COOKED_BEEF, "§dСтейк «Горный Спринтер»")
                .lore(List.of("§7Идеально прожаренное мясо со специями.", "", "§⚡ Эффект (8 минут):", " §e• Скорость II", " §e• Прыгучесть I"))
                .effect(new PotionEffect(PotionEffectType.SPEED, 9600, 1))
                .effect(new PotionEffect(PotionEffectType.JUMP_BOOST, 9600, 0))
                .effect(new PotionEffect(PotionEffectType.SATURATION, 9600, 0))
                .consumeMessage("§d⚡ Стейк «Горный Спринтер» дал вам взрывную скорость и легкость!")
                .campfireRaw("rp_raw_sprinter", Material.BEEF, "§dСырой Стейк «Спринтер»",
                        List.of("§7Мясо замариновано в огненных специях.", "§cТребует идеальной прожарки на КОСТРЕ!"))
                .build());

        registerDish(new CustomDish.Builder("kebab", Material.COOKED_CHICKEN, "§6РП-Шашлык на углях")
                .lore(List.of("§7Ароматные сочные куски мяса на шампуре.", "", "§⚡ Эффект (10 минут):", " §e• +2 Золотых сердца"))
                .effect(new PotionEffect(PotionEffectType.ABSORPTION, 12000, 1))
                .consumeMessage("§6⚡ Вы съели сочный Шашлык. Вы чувствуете прилив жизненных сил.")
                .campfireRaw("rp_raw_kebab", Material.STONE_BUTTON, "§6Сырой Шашлык на Шампуре",
                        List.of("§7Сырое мясо, насаженное на палку.", "§cТребует идеальной прожарки на КОСТРЕ!"))
                .build());

        registerDish(new CustomDish.Builder("royal_carp", Material.COOKED_COD, "§eИмператорский Запеченный Карп")
                .lore(List.of("§7Рыба с хрустящей золотистой корочкой.", "", "§⚡ Эффект (15 минут):", " §e• Подводное дыхание", " §e• Грация дельфина"))
                .effect(new PotionEffect(PotionEffectType.WATER_BREATHING, 18000, 0))
                .effect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 18000, 0))
                .consumeMessage("§b⚡ Императорский запеченный карп позволяет вам чувствовать себя в воде как дома!")
                .campfireRaw("rp_raw_carp", Material.COD, "§bСырой Подготовленный Карп",
                        List.of("§7Рыба очищена и вымочена в сиропе.", "§cТребует идеальной прожарки на КОСТРЕ!"))
                .build());

        registerDish(new CustomDish.Builder("dark_beer", Material.POTION, "§6Темное Крафтовое Пиво")
                .lore(List.of("§7Крепкий хмельной напиток.", "", "§⚡ Эффекты:", " §e• Сила II (3 мин)", " §e• Стойкость I (3 мин)"))
                .effect(new PotionEffect(PotionEffectType.STRENGTH, 3600, 1))
                .effect(new PotionEffect(PotionEffectType.RESISTANCE, 3600, 0))
                .effect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0))
                .effect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0))
                .consumeSound(Sound.ENTITY_PLAYER_BURP)
                .consumeMessage("§6§o*Ухх!* Напиток бьет точно в голову. Вы чувствуете дикую силу в руках, но земля уходит из-под ног!")
                .build());

        registerDish(new CustomDish.Builder("beer_wort", Material.POTION, "§6Сырое Пивное Сусло")
                .lore(List.of("§7Густой сладковатый концентрат злаков.", "§7Получен путем варки в котле.", "", "§cНепригодно для питья! Требует", "§cсозревания в Бочке Брожения."))
                .consumeMessage("§cВы попытались сделать глоток сырого сусла. Оно дико приторное и вяжет рот. Фу!")
                .build());

        // ==========================================
        // КАТЕГОРИЯ: ДОБАВЛЕННЫЕ РП БЛЮДА И НАПИТКИ
        // ==========================================

        // 1. Армейский Сухпаёк (КОСТЁР)
        registerDish(new CustomDish.Builder("soldier_bread", Material.BREAD, "§7Армейский Сухпаёк: Обжаренный хлеб")
                .lore(List.of("§7Чрезвычайно питательные хрустящие гренки с солью.", "", "§⚡ Эффект (10 минут):", " §e• Спешка I"))
                .effect(new PotionEffect(PotionEffectType.HASTE, 12000, 0))
                .consumeMessage("§7§o*Хруст...* Соленый сухой армейский хлеб бодрит рецепторы! Руки двигаются быстрее.")
                .campfireRaw("rp_raw_soldier_bread", Material.BREAD, "§7Чёрствый Армейский Паёк",
                        List.of("§7Сухой хлеб, обсыпанный крупной солью.", "§cТребует обжарки на КОСТРЕ для размягчения!"))
                .build());

        // 2. Картофель «По-разбойничьи» (КОСТЁР)
        registerDish(new CustomDish.Builder("ash_potato", Material.BAKED_POTATO, "§eПечёный картофель «По-разбойничьи»")
                .lore(List.of("§7Картошка, запечённая на углях прямо в золе.", "", "§⚡ Эффект (5 минут):", " §e• Поглощение I (+2 Сердца)"))
                .effect(new PotionEffect(PotionEffectType.ABSORPTION, 6000, 0))
                .consumeMessage("§e§o*Осторожно, горячая!* Вы счищаете сажу и съедаете картофель. Вы стали выносливее.")
                .campfireRaw("rp_raw_ash_potato", Material.POISONOUS_POTATO, "§8Картофель в золе",
                        List.of("§7Завернутый в плотную обертку сырой картофель.", "§cТребует идеального томления в углях КОСТРА!"))
                .build());

        // 3. Охлаждающий Огуречный Лимонад (ЗЕЛЬЕВАРКА)
        registerDish(new CustomDish.Builder("cucumber_lemonade", Material.POTION, "§bОхлаждающий Огуречный Лимонад")
                .lore(List.of("§7Ледяной тонизирующий напиток, спасающий от жары.", "", "§⚡ Эффект (6 минут):", " §e• Насыщение (Бесконечный бег)", " §e• Прыгучесть I"))
                .effect(new PotionEffect(PotionEffectType.SATURATION, 7200, 0))
                .effect(new PotionEffect(PotionEffectType.JUMP_BOOST, 7200, 0))
                .consumeMessage("§b⚡ Ледяная свежесть лимонада придает вам легкости! Ноги сами несут вас вперед.")
                .build());

        // 4. Хвойный Противоцинговый Отвар (ЗЕЛЬЕВАРКА)
        registerDish(new CustomDish.Builder("pine_decoction", Material.POTION, "§2Хвойный Противоцинговый Отвар")
                .lore(List.of("§7Медицинское средство. Очищает кровь от токсинов.", "", "§⚡ Эффект:", " §e• Моментально снимает яд и тьму", " §e• Стойкость I (1.5 минуты)"))
                .effect(new PotionEffect(PotionEffectType.RESISTANCE, 1800, 0))
                // Снятие эффектов допишем отдельно через слушатель
                .consumeMessage("§2⚡ Кислый хвойный отвар покалывает язык. Организм полностью очищен от заразы!")
                .build());
    }
}