package ru.politaboba.serverSystem.item.equipment;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.item.equipment.model.CustomEquipment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EquipmentManager {

    private final ServerSystem plugin;
    private final Map<String, CustomEquipment> equipmentRegistry = new HashMap<>();

    public EquipmentManager(ServerSystem plugin) {
        this.plugin = plugin;
        registerAllEquipment();
    }

    // НОВОЕ: Этот метод нужен нашей команде для Tab-комплектера
    public Set<String> getRegisteredIds() {
        return equipmentRegistry.keySet();
    }

    public CustomEquipment getEquipmentById(String id) {
        return equipmentRegistry.get(id);
    }

    private void register(CustomEquipment equipment) {
        equipmentRegistry.put(equipment.getId(), equipment);
    }

    /**
     * Автоматическая регистрация всех рецептов в ядре Bukkit
     */
    public void registerAllRecipes() {
        equipmentRegistry.values().forEach(eq -> {
            if (eq.getRecipeShape() == null) return;

            NamespacedKey key = new NamespacedKey(plugin, eq.getId() + "_craft");
            if (Bukkit.getRecipe(key) != null) return;

            ItemStack result = eq.createItemStack(plugin);
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(eq.getRecipeShape());

            eq.getRecipeIngredients().forEach(recipe::setIngredient);
            Bukkit.addRecipe(recipe);
        });
    }

    /**
     * Ваша база данных оружия и шаблонов. Расширяйте её здесь!
     */
    private void registerAllEquipment() {
        // 1. ТЯЖЕЛАЯ АЛМАЗНАЯ АЛЕБАРДА
        register(new CustomEquipment.Builder("rp_halberd", Material.DIAMOND_AXE, "§b§lТяжелая Алмазная Алебарда")
                .lore(List.of(
                        "§7Древковое рубящее оружие стражи.", "",
                        "§⚡ Особенности:",
                        " §e• §aУвеличенная дистанция атаки (+2.0 б.)",
                        " §e• §aУстойчивость к отбрасыванию (+50%)", "",
                        "§cВысокий урон, но долгая перезамах-атака."
                ))
                .attribute(Attribute.ENTITY_INTERACTION_RANGE, 2.0)
                .attribute(Attribute.ATTACK_DAMAGE, 11.0)
                .attribute(Attribute.ATTACK_SPEED, -3.2)
                .attribute(Attribute.KNOCKBACK_RESISTANCE, 0.5)
                .craft(new String[]{"DBD", " S ", " S "},
                        'D', Material.DIAMOND,
                        'B', Material.DIAMOND_BLOCK,
                        'S', Material.BREEZE_ROD)
                .build());

        // 2. КУЗНЕЧНЫЙ ШАБЛОН НАЕМНИКА
        register(new CustomEquipment.Builder("mercenary_template", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "§4§lКузнечный Шаблон: Улучшение Наёмника")
                .lore(List.of(
                        "§7Позволяет модифицировать броню под нужды наёмников.", "",
                        "§eПрименимо в кузнечном столе:",
                        " §7• Объедините с элементом брони",
                        " §7• Требуется: §b1 Алмаз§7 в качестве материала", "",
                        "§c§lПолный сет экипировки наёмника даёт:",
                        " §e• §aСкорость II (+20% к бегу)",
                        " §e• §aВыносливость (+10 доп. сердца)",
                        " §e• §aБоевая регенерация (Регенерация I)"
                ))
                .build());

        // 3. КОПЬЕ ЗЕФИРА
        register(new CustomEquipment.Builder("rp_zephyr_spear", Material.TRIDENT, "§b§lКопьё Зефира")
                .lore(List.of(
                        "§7Легкое копьё, выкованное из энергии шторма.", "",
                        "§⚡ Особенности:",
                        " §e• §aАтака в падении подбрасывает врага (3-4 б.)", "",
                        "§bОседлай силу яростного ветра."
                ))
                .attribute(Attribute.ATTACK_DAMAGE, 8.0)
                .attribute(Attribute.ATTACK_SPEED, -2.2)
                .build());

        // 4. КУЗНЕЧНЫЙ ШАБЛОН ЛОВЦА ВЕТРОВ
        register(new CustomEquipment.Builder("wind_catcher_template", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "§b§lКузнечный Шаблон: Ловец Ветров")
                .lore(List.of(
                        "§7Насыщает броню аэродинамическими свойствами.", "",
                        "§eПрименимо в кузнечном столе:",
                        " §7• Объедините с элементом брони",
                        " §7• Требуется: §b1 Алмаз§7 в качестве материала", "",
                        "§b§lПолный сет экипировки Ловца Ветров даёт:",
                        " §e• §aАбсолютный иммунитет к урону от падения",
                        " §e• §aДвойной прыжок (Рывок ветра) [КД: 7 сек]"
                ))
                .build());


        // 5. ТОПОР РАСКАЛЫВАТЕЛЯ ЗАМКОВ
        register(new CustomEquipment.Builder("artifact_axe", Material.DIAMOND_AXE, "§6§lТопор Раскалывателя Замков")
                .lore(List.of(
                        "§7Древнее тяжелое орудие разрушителей крепостей.", "",
                        "§⚡ Особенности:",
                        " §e• §aУвеличенная дистанция атаки (+1.5 б.)",
                        " §e• §cМедленная скорость взмаха (-3.4)", "",
                        "§6§lАктивный навык (ПКМ): §e⚡ Разверзшийся Ад",
                        " §7• Вызывает волну челюстей Вызывателя вперед.",
                        " §7• §bПробивает и отключает щиты блоков.",
                        " §7• §bОглушает цели (Замедление IV + Тьма на 3 сек)."
                ))
                .attribute(Attribute.ENTITY_INTERACTION_RANGE, 1.5)
                .attribute(Attribute.ATTACK_DAMAGE, 12.0) // Мощный урон
                .attribute(Attribute.ATTACK_SPEED, -3.4)   // Но очень медленный
                .build());

        // 6. МАГНИТНЫЙ ПАЛАШ
        register(new CustomEquipment.Builder("magnetic_greatsword", Material.NETHERITE_SWORD, "§d§lМагнитный Палаш")
                .lore(List.of(
                        "§7Двуручный клинок, выкованный вокруг",
                        "§7мощного природного магнетита.", "",
                        "§⚡ Особенности:",
                        " §e• §aУвеличенная дистанция атаки (+1.0 б.)",
                        " §e• §cСниженная скорость взмаха (-3.0)", "",
                        "§d§lАктивный навык (ПКМ): §e⚡ Магнитный Коллапс",
                        " §7• Сканирует врагов в радиусе 12 блоков перед собой.",
                        " §7• Находит цели в §fЖелезной§7, §bАлмазной§7 или §5Незеритовой§7 броне.",
                        " §7• §dСтягивает всех найденных врагов в единую точку§7 посередине!",
                        " §7• Наносит стянутым целям урон от соударения."
                ))
                .attribute(Attribute.ENTITY_INTERACTION_RANGE, 1.0)
                .attribute(Attribute.ATTACK_DAMAGE, 9.0)
                .attribute(Attribute.ATTACK_SPEED, -3.0)
                .build());

        // 7. ЛУК БУРИ
        register(new CustomEquipment.Builder("rp_storm_bow", Material.BOW, "§b§lЛук Бури")
                .lore(List.of(
                        "§7Древнее стрелковое оружие, благословленное силой шторма.", "",
                        "§⚡ Особенности:",
                        " §e• §aСтрелы призывают удар молнии при попадании.",
                        " §e• §cОбладает кулдауном на перезарядку сил природы.", "",
                        "§bПризови ярость небес на своих врагов."
                ))
                .build());
    }
}