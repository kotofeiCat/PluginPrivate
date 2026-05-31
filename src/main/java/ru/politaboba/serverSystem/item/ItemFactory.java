package ru.politaboba.serverSystem.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class ItemFactory {

    private final ServerSystem plugin;

    public ItemFactory(ServerSystem plugin) {
        this.plugin = plugin;
    }

    // Метод создания Дымовой Шашки
    public ItemStack createSmokeBomb() {
        ItemStack smokeBomb = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = smokeBomb.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§8§lСлепой Порошок");

            List<String> lore = new ArrayList<>();
            lore.add("§7Уникальное приспособление шпионов.");
            lore.add("");
            lore.add("§7Взрывается при клике под себя,");
            lore.add("§7создавая густую дымовую завесу");
            lore.add("§7и ослепляя врагов.");
            lore.add("");
            lore.add("§c[Использование: ПКМ об землю]");
            meta.setLore(lore);

            NamespacedKey key = new NamespacedKey(plugin, "rp_smoke_bomb");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            smokeBomb.setItemMeta(meta);
        }

        return smokeBomb;
    }

    public ItemStack createHalberd() {
        ItemStack halberd = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = halberd.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§b§lТяжелая Алмазная Алебарда");

            List<String> lore = new ArrayList<>();
            lore.add("§7Древковое рубящее оружие стражи.");
            lore.add("");
            lore.add("§⚡ Особенности:");
            lore.add(" §e• §aУвеличенная дистанция атаки (+2.0 б.)");
            lore.add(" §e• §aУстойчивость к отбрасыванию (+50%)");
            lore.add("");
            lore.add("§cВысокий урон, но долгая перезамах-атака.");
            meta.setLore(lore);

            // 1. Дистанция атаки
            NamespacedKey reachKey = new NamespacedKey(plugin, "halberd_reach");
            AttributeModifier reachModifier = new AttributeModifier(
                    reachKey,
                    2.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, reachModifier);
            meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, reachModifier);

            // 2. Урон (11 единиц)
            NamespacedKey damageKey = new NamespacedKey(plugin, "halberd_damage");
            AttributeModifier damageModifier = new AttributeModifier(
                    damageKey,
                    11.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

            // 3. Скорость атаки
            NamespacedKey speedKey = new NamespacedKey(plugin, "halberd_speed");
            AttributeModifier speedModifier = new AttributeModifier(
                    speedKey,
                    -3.2,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

            // 4. Стойкость к откидыванию (Knockback Resistance)
            NamespacedKey knockKey = new NamespacedKey(plugin, "halberd_knockback");
            AttributeModifier knockModifier = new AttributeModifier(
                    knockKey,
                    0.5,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockModifier);

            NamespacedKey identityKey = new NamespacedKey(plugin, "rp_halberd");
            meta.getPersistentDataContainer().set(identityKey, PersistentDataType.BYTE, (byte) 1);

            halberd.setItemMeta(meta);
        }

        return halberd;
    }

    public ItemStack createCookbook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta != null) {
            meta.setTitle("§6§lКулинарная Книга");
            meta.setAuthor("§eМинистерство Культуры");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);

            List<String> lore = new ArrayList<>();
            lore.add("§7Полное руководство по кулинарии");
            lore.add("§7и созданию кастомных предметов.");
            lore.add("");
            lore.add("§6[Нажми ПКМ, чтобы открыть]");
            meta.setLore(lore);

            List<String> pages = new ArrayList<>();

            // СТРАНИЦА 1: ВВЕДЕНИЕ И ОГЛАВЛЕНИЕ
            pages.add(
                    "§1§lИНСТРУКЦИЯ ПО ВАРКЕ\n" +
                            "§0─────────────────\n" +
                            "Данная книга является\n" +
                            "официальным техническим\n" +
                            "руководством сервера.\n\n" +
                            "§lРАЗДЕЛЫ:\n" +
                            "§01. Крафты полуфабрикатов\n" +
                            "2. Инструкция: Костёр\n" +
                            "3. Рецепты блюд костра\n" +
                            "4. Стойка и Сборка\n" +
                            "5. Особое Снаряжение\n" +
                            "─────────────────\n" +
                            "    §8Версия спецификации: 3.0"
            );

            // СТРАНИЦА 2: ВЕРСТАК — ПОЛУФАБРИКАТЫ (ЧАСТЬ 1)
            pages.add(
                    "§6§l1. ПОЛУФАБРИКАТЫ (1)\n" +
                            "§0─────────────────\n" +
                            "Рецепты сборки в верстаке\n" +
                            "перед термообработкой:\n\n" +
                            "§lСырой Стейк Спринтер:\n" +
                            "§0- Сырая говядина x1\n" +
                            "- Коричневый гриб x1\n" +
                            "- Огненный порошок x1\n" +
                            "§d[Выход: Сырая говядина]\n\n" +
                            "§lСырой Шампур Шашлыка:\n" +
                            "§0- Палка x1\n" +
                            "- Сырая говядина x1\n" +
                            "- Костная мука x1\n" +
                            "§6[Выход: Каменная кнопка]"
            );

            // СТРАНИЦА 3: ВЕРСТАК — ПОЛУФАБРИКАТЫ (ЧАСТЬ 2)
            pages.add(
                    "§6§l1. ПОЛУФАБРИКАТЫ (2)\n" +
                            "§0─────────────────\n" +
                            "§lСырой Подготовл. Карп:\n" +
                            "§0- Сырая треска x1\n" +
                            "- Короткая трава x1\n" +
                            "- Бутылочка мёда x1\n" +
                            "§b[Выход: Сырая треска]\n\n" +
                            "§lМятная Заварка:\n" +
                            "§0- Короткая трава x1\n" +
                            "- Сахар x1\n" +
                            "§a[Выход: Маринов. паучий глаз]"
            );

            // СТРАНИЦА 4: ТЕХНИЧЕСКИЙ РЕГЛАМЕНТ ГОТОВКИ НА КОСТРЕ
            pages.add(
                    "§c§l2. ИНСТРУКЦИЯ: КОСТЁР\n" +
                            "§0─────────────────\n" +
                            "§lПОРЯДОК ДЕЙСТВИЙ:\n" +
                            "§01. Взять сырую заготовку.\n" +
                            "2. Кликнуть ПКМ по костру.\n" +
                            "3. Отсчитать время таймера.\n" +
                            "4. Снять предмет §lПУСТОЙ РУКОЙ§0.\n\n" +
                            "§lТАЙМИНГИ И СТАТУСЫ:\n" +
                            "§0• 0 - 9 сек: §eСырое§0 (Яд)\n" +
                            "• 10 - 14 сек: §a§lИДЕАЛ§0 (Бафф)\n" +
                            "• 15+ сек: §4Угли§0 (Брак)"
            );

            // СТРАНИЦА 5: ТЕХНИЧЕСКИЕ КАРТЫ ГОТОВЫХ БЛЮД (КОСТЁР)
            pages.add(
                    "§2§l3. РЕЦЕПТЫ КОСТРА\n" +
                            "§0─────────────────\n" +
                            "Параметры идеального съёма:\n\n" +
                            "§lСтейк Спринтер:\n" +
                            "§0Заготовка Стейка -> 10-14с\n" +
                            "§dДлительность эффекта: 8м\n" +
                            "Модификатор: Скорость II + Прыжок\n\n" +
                            "§lШашлык на углях:\n" +
                            "§0Шампур Шашлыка -> 10-14с\n" +
                            "§6Длительность эффекта: 10м\n" +
                            "Модификатор: Поглощение II (+2 сердца)\n\n" +
                            "§lЗапечённый Карп:\n" +
                            "§0Заготовка Карпа -> 10-14с\n" +
                            "§bДлительность эффекта: 15м\n" +
                            "Модификатор: Водный вдох + Дельфин"
            );

            // СТРАНИЦА 6: ВАРОЧНАЯ СТОЙКА И СБОРКА БЕЗ ТЕРМООБРАБОТКИ
            pages.add(
                    "§e§l4. СТОЙКА И СБОРКА\n" +
                            "§0─────────────────\n" +
                            "§lМятный Чай:\n" +
                            "§0Инструмент: Варочная стойка\n" +
                            "Верхний слот: Мятная заварка\n" +
                            "Нижние слоты: Колбы с водой\n" +
                            "§2Длительность эффекта: 10м\n" +
                            "Эффект: Регенерация I\n\n" +
                            "§lСытная Шаурма:\n" +
                            "§0Инструмент: Верстак (без формы)\n" +
                            "Компоненты:\n" +
                            "- Хлеб x1\n" +
                            "- Жареная говядина x1\n" +
                            "- Короткая трава x1\n" +
                            "§eДлительность эффекта: 12м"
            );

            // СТРАНИЦА 7: СПЕЦИАЛЬНОЕ ВООРУЖЕНИЕ И СНАРЯЖЕНИЕ
            pages.add(
                    "§4§l5. ОСОБОЕ СНАРЯЖЕНИЕ\n" +
                            "§0─────────────────\n" +
                            "§lСлепой Порошок:\n" +
                            "§0Крафт: Верстак (бесформенный)\n" +
                            "Ингредиенты: Порох + Уголь +\n" +
                            "Ракета + Железный самородок\n" +
                            "§8[Выход: Огненный шар]\n\n" +
                            "§lАлмазная Алебарда:\n" +
                            "§0Крафт: Верстак (Форма Т)\n" +
                            "Схема:\n" +
                            "§lАлмаз | Алмаз. блок | Алмаз\n" +
                            "Пусто  | Стерж. бриза | Пусто\n" +
                            "Пусто  | Стерж. бриза | Пусто\n" +
                            "§b[Свойство: Дистанция атаки +2.0]"
            );

            meta.setPages(pages);

            NamespacedKey key = new NamespacedKey(plugin, "rp_cookbook");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            book.setItemMeta(meta);
        }

        return book;
    }
}