package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class EquipmentSmithingListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey templateMercKey;
    private final NamespacedKey armorMercMarkKey;

    public EquipmentSmithingListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.templateMercKey = new NamespacedKey(plugin, "mercenary_template");
        this.armorMercMarkKey = new NamespacedKey(plugin, "mercenary_armor_piece");
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack template = event.getInventory().getItem(0);
        ItemStack armor = event.getInventory().getItem(1);
        ItemStack ingredient = event.getInventory().getItem(2);

        if (template == null || armor == null || armor.getType() == Material.AIR) return;

        if (!template.hasItemMeta()) return;
        var pdc = template.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(templateMercKey, PersistentDataType.BYTE)) return;

        if (ingredient == null || ingredient.getType() != Material.DIAMOND) {
            event.setResult(null);
            return;
        }

        ItemStack result = event.getResult() == null ? armor.clone() : event.getResult();
        ItemMeta meta = result.getItemMeta();

        if (meta != null && !meta.getPersistentDataContainer().has(armorMercMarkKey, PersistentDataType.BYTE)) {
            Material type = result.getType();
            EquipmentSlotGroup slotGroup = convertMaterialToSlotGroup(type);
            if (slotGroup == null) return;

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§4§l⚔ Экипировка Наёмника");
            lore.add(" §7• Модификатор скорости: §a+5%");
            // ИСПРАВЛЕНО: Изменили текст в лоре под новые характеристики (+5.0 HP = 2.5 сердца на предмет)
            lore.add(" §7• Модификатор здоровья: §a+5.0 ❤ §7(2.5 Сердца)");
            lore.add(" §8• Полный сет даёт +1 дополнительную полоску ХП (10 сердец)");
            lore.add(" §8• Полный сет: Тактический Камуфляж на 3 минуты.");
            lore.add(" §8  (Присядьте на 3 сек без движений для активации)");
            meta.setLore(lore);

            applyVanillaAttributes(type, meta, slotGroup);

            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "merc_spd_" + type.name().toLowerCase()), 0.005, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

            // ИСПРАВЛЕНО: Изменили значение модификатора с 1.5 на 5.0.
            // 4 предмета по 5.0 HP = 20 HP в сумме (ровно 10 ванильных сердец / 1 полоска)
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                    new NamespacedKey(plugin, "merc_hp_" + type.name().toLowerCase()), 5.0, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

            meta.getPersistentDataContainer().set(armorMercMarkKey, PersistentDataType.BYTE, (byte) 1);
            result.setItemMeta(meta);

            event.setResult(result);
        }
    }

    private void applyVanillaAttributes(Material type, ItemMeta meta, EquipmentSlotGroup slotGroup) {
        double armor = 0;
        double toughness = 0;
        double kbRes = 0;
        String name = type.name();

        if (name.startsWith("DIAMOND_")) {
            toughness = 2.0;
            if (name.endsWith("HELMET")) armor = 3.0;
            else if (name.endsWith("CHESTPLATE")) armor = 8.0;
            else if (name.endsWith("LEGGINGS")) armor = 6.0;
            else if (name.endsWith("BOOTS")) armor = 3.0;
        } else if (name.startsWith("NETHERITE_")) {
            toughness = 3.0;
            kbRes = 0.1;
            if (name.endsWith("HELMET")) armor = 3.0;
            else if (name.endsWith("CHESTPLATE")) armor = 8.0;
            else if (name.endsWith("LEGGINGS")) armor = 6.0;
            else if (name.endsWith("BOOTS")) armor = 3.0;
        }
        if (armor > 0) meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin, "vanilla_arm_" + name.toLowerCase()), armor, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        if (toughness > 0) meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(plugin, "vanilla_tgh_" + name.toLowerCase()), toughness, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        if (kbRes > 0) meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(plugin, "vanilla_kbr_" + name.toLowerCase()), kbRes, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
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