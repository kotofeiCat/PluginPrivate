package ru.politaboba.serverSystem.item.combat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.Collection;

public class SmokeBombListener implements Listener {

    private final ServerSystem plugin;

    public SmokeBombListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerUseSmokeBomb(PlayerInteractEvent event) {
        // Проверяем, что игрок кликнул ПКМ по блоку
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // ПРОВЕРКА НАШЕГО NBT-ТЕГА
        NamespacedKey key = new NamespacedKey(plugin, "rp_smoke_bomb");
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }

        // ОТМЕНЯЕМ ВАНИЛЬНЫЙ ВЫСТРЕЛ ОГНЕННЫМ ШАРОМ
        event.setCancelled(true);
        Player spy = event.getPlayer();
        Location loc = spy.getLocation();

        // 1. ЗАПУСКАЕМ ВИЗУАЛЬНЫЕ ЭФФЕКТЫ (Дым и звуки)
        // Спавним густой дым в радиусе 3 блоков
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 200, 3.0, 1.0, 3.0, 0.05);
        // Добавляем звуки пшика и удара
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CANDLE_EXTINGUISH, 1.0f, 0.5f);

        // 2. НАКЛАДЫВАЕМ РП-ЭФФЕКТЫ НА ЛЮДЕЙ ВОКРУГ
        // Находим всех существ в радиусе 6 блоков
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 6.0, 4.0, 6.0);
        for (Entity entity : nearby) {
            if (entity instanceof Player) {
                Player target = (Player) entity;

                // Если это не сам шпион - ослепляем
                if (!target.equals(spy)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 2)); // Ослепление на 6 секунд
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));      // Медлительность на 4 секунды
                    target.sendMessage("§c§oВас ослепил густой дым!");
                }
            }
        }

        // 3. ДАЕМ ШПИОНУ БАФФ К СКОРОСТИ ДЛЯ ПОБЕГА
        spy.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1)); // Скорость II на 5 секунд

        // 4. ТРАТИМ ОДНУ ШАШКУ ИЗ РУКИ
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            spy.getInventory().setItemInMainHand(null);
        }
        spy.sendMessage("§8Взрыв! Вы использовали Слепой Порошок.");
    }
}
