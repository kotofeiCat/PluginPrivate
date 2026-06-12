package ru.politaboba.serverSystem.item.food.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class CustomDish {
    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final List<PotionEffect> effects;
    private final String consumeMessage;
    private final Sound consumeSound;

    // Свойства сырой заготовки для костра (опционально)
    private final String rawId;
    private final Material rawMaterial;
    private final String rawDisplayName;
    private final List<String> rawLore;

    private CustomDish(Builder builder) {
        this.id = builder.id;
        this.material = builder.material;
        this.displayName = builder.displayName;
        this.lore = builder.lore;
        this.effects = builder.effects;
        this.consumeMessage = builder.consumeMessage;
        this.consumeSound = builder.consumeSound;
        this.rawId = builder.rawId;
        this.rawMaterial = builder.rawMaterial;
        this.rawDisplayName = builder.rawDisplayName;
        this.rawLore = builder.rawLore;
    }

    public String getId() { return id; }
    public String getRawId() { return rawId; }
    public List<PotionEffect> getEffects() { return effects; }
    public String getConsumeMessage() { return consumeMessage; }
    public Sound getConsumeSound() { return consumeSound; }

    // Создание готового блюда
    public ItemStack createCookedItem(ServerSystem plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Создание сырого ингредиента
    public ItemStack createRawItem(ServerSystem plugin) {
        if (rawId == null) return null;
        ItemStack item = new ItemStack(rawMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(rawDisplayName);
            meta.setLore(rawLore);
            NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, rawId);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Класс-строитель (Builder)
    public static class Builder {
        private final String id;
        private final Material material;
        private final String displayName;
        private List<String> lore = new ArrayList<>();
        private final List<PotionEffect> effects = new ArrayList<>();
        private String consumeMessage;
        private Sound consumeSound;

        private String rawId;
        private Material rawMaterial;
        private String rawDisplayName;
        private List<String> rawLore = new ArrayList<>();

        public Builder(String id, Material material, String displayName) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
        }

        public Builder lore(List<String> lore) { this.lore = lore; return this; }
        public Builder effect(PotionEffect effect) { this.effects.add(effect); return this; }
        public Builder consumeMessage(String msg) { this.consumeMessage = msg; return this; }
        public Builder consumeSound(Sound sound) { this.consumeSound = sound; return this; }

        public Builder campfireRaw(String rawId, Material rawMaterial, String rawDisplayName, List<String> rawLore) {
            this.rawId = rawId;
            this.rawMaterial = rawMaterial;
            this.rawDisplayName = rawDisplayName;
            this.rawLore = rawLore;
            return this;
        }

        public CustomDish build() { return new CustomDish(this); }
    }
}