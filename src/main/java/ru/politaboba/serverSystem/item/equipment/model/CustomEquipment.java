package ru.politaboba.serverSystem.item.equipment.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomEquipment {
    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final Map<Attribute, Double> attributes;

    // Данные для генерации крафта
    private final String[] recipeShape;
    private final Map<Character, Material> recipeIngredients;

    private CustomEquipment(Builder builder) {
        this.id = builder.id;
        this.material = builder.material;
        this.displayName = builder.displayName;
        this.lore = builder.lore;
        this.attributes = builder.attributes;
        this.recipeShape = builder.recipeShape;
        this.recipeIngredients = builder.recipeIngredients;
    }

    public String getId() { return id; }
    public String[] getRecipeShape() { return recipeShape; }
    public Map<Character, Material> getRecipeIngredients() { return recipeIngredients; }

    public ItemStack createItemStack(ServerSystem plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(displayName);
        meta.setLore(lore);

        // Динамическое наложение боевых атрибутов
        attributes.forEach((attribute, value) -> {
            NamespacedKey attrKey = new NamespacedKey(plugin, id + "_" + attribute.getKey().getKey());
            AttributeModifier modifier = new AttributeModifier(
                    attrKey,
                    value,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(attribute, modifier);

            // Если настраивается дистанция атаки, дублируем её и на блоки
            if (attribute == Attribute.ENTITY_INTERACTION_RANGE) {
                meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, modifier);
            }
        });

        // Запись системного NBT-ключа
        NamespacedKey identityKey = new NamespacedKey(plugin, id);
        meta.getPersistentDataContainer().set(identityKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static class Builder {
        private final String id;
        private final Material material;
        private final String displayName;
        private List<String> lore = new ArrayList<>();
        private final Map<Attribute, Double> attributes = new HashMap<>();
        private String[] recipeShape;
        private final Map<Character, Material> recipeIngredients = new HashMap<>();

        public Builder(String id, Material material, String displayName) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
        }

        public Builder lore(List<String> lore) { this.lore = lore; return this; }

        public Builder attribute(Attribute attribute, double value) {
            this.attributes.put(attribute, value);
            return this;
        }

        public Builder craft(String[] shape, Object... ingredients) {
            this.recipeShape = shape;
            for (int i = 0; i < ingredients.length; i += 2) {
                this.recipeIngredients.put((Character) ingredients[i], (Material) ingredients[i+1]);
            }
            return this;
        }

        public CustomEquipment build() { return new CustomEquipment(this); }
    }
}