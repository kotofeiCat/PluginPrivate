package ru.politaboba.serverSystem.item.combat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent; // Заменили событие
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class MercenaryArmorListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey templateKey;
    private final NamespacedKey armorMarkKey;

    public MercenaryArmorListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.templateKey = new NamespacedKey(plugin, "mercenary_template");
        this.armorMarkKey = new NamespacedKey(plugin, "mercenary_armor_piece");

        startRegenTask();
    }

    // ==========================================
    // УНИВЕРСАЛЬНАЯ ЛОГИКА КУЗНЕЧНОГО СТОЛА
    // ==========================================
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        // Получаем предметы из слотов кузнечного стола напрямую через инвентарь события
        ItemStack template = event.getInventory().getItem(0); // Слот шаблона
        ItemStack armor = event.getInventory().getItem(1);    // Слот брони
        ItemStack result = event.getResult();                 // Результат

        if (template == null || armor == null || result == null) return;

        // Проверяем наш кастомный шаблон наёмника
        if (template.hasItemMeta() && template.getItemMeta().getPersistentDataContainer().has(templateKey, PersistentDataType.BYTE)) {

            ItemMeta resultMeta = result.getItemMeta();
            if (resultMeta != null) {
                // Если на предмете уже есть наша метка — не накладываем её повторно, чтобы избежать багов
                if (resultMeta.getPersistentDataContainer().has(armorMarkKey, PersistentDataType.BYTE)) return;

                Material type = result.getType();
                EquipmentSlotGroup slotGroup = convertMaterialToSlotGroup(type);

                if (slotGroup == null) return;

                // Добавляем РП описание эффектов предмета
                List<String> lore = resultMeta.hasLore() ? resultMeta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§4§l⚔ Экипировка Наёмника");
                lore.add(" §7• Модификатор скорости: §a+5%");
                lore.add(" §7• Модификатор здоровья: §a+1.5 🧭");
                resultMeta.setLore(lore);

                // Баффы полного сета: +0.02 скорости и +6.0 HP (3 сердца)
                double speedBonus = 0.005;
                double healthBonus = 1.5;

                // 1. Привязываем Атрибут Скорости
                NamespacedKey speedModifierKey = new NamespacedKey(plugin, "merc_speed_" + type.name().toLowerCase());
                AttributeModifier speedModifier = new AttributeModifier(
                        speedModifierKey,
                        speedBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        slotGroup
                );
                resultMeta.addAttributeModifier(Attribute.MOVEMENT_SPEED, speedModifier);

                // 2. Привязываем Атрибут Здоровья
                NamespacedKey healthModifierKey = new NamespacedKey(plugin, "merc_health_" + type.name().toLowerCase());
                AttributeModifier healthModifier = new AttributeModifier(
                        healthModifierKey,
                        healthBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        slotGroup
                );
                resultMeta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier);

                // 3. Ставим скрытую PDC-метку
                resultMeta.getPersistentDataContainer().set(armorMarkKey, PersistentDataType.BYTE, (byte) 1);

                result.setItemMeta(resultMeta);

                // Принудительно обновляем результат в инвентаре кузнечного стола
                event.setResult(result);
            }
        }
    }

    // ==========================================
    // СЕКУНДНЫЙ ТАЙМЕР ДЛЯ РЕГЕНЕРАЦИИ (ТРЕБУЕТСЯ ПОЛНЫЙ СЕТ)
    // ==========================================
    private void startRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int mercenaryPieces = 0;

                    for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
                        if (armorPiece != null && armorPiece.getType() != Material.AIR && armorPiece.hasItemMeta()) {
                            if (armorPiece.getItemMeta().getPersistentDataContainer().has(armorMarkKey, PersistentDataType.BYTE)) {
                                mercenaryPieces++;
                            }
                        }
                    }

                    if (mercenaryPieces == 4) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.REGENERATION,
                                40,
                                0,
                                false,
                                false,
                                true
                        ));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private EquipmentSlotGroup convertMaterialToSlotGroup(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET")) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return null;
    }
}