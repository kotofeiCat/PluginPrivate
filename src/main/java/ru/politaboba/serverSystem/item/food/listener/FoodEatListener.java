package ru.politaboba.serverSystem.item.food.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.item.food.model.CustomDish;

public class FoodEatListener implements Listener {

    private final ServerSystem plugin;

    public FoodEatListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
        String dishType = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (dishType == null) return;

        CustomDish dish = plugin.getFoodManager().getDishById(dishType);
        if (dish == null) return;

        Player player = event.getPlayer();

        // Кастомное уникальное поведение для Хвойного Отвара (очищение негативных эффектов)
        if (dishType.equals("pine_decoction")) {
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.WITHER);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        // Накладываем ванильные эффекты из ООП-базы
        for (PotionEffect effect : dish.getEffects()) {
            player.addPotionEffect(effect);
        }

        if (dish.getConsumeMessage() != null) {
            player.sendMessage(dish.getConsumeMessage());
        }

        if (dish.getConsumeSound() != null) {
            player.playSound(player.getLocation(), dish.getConsumeSound(), 1.0f, 1.0f);
        }
    }
}