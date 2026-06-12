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
import org.bukkit.inventory.EquipmentSlot;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // ИСПРАВЛЕНО: Блокируем двойной клик от левой руки
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        NamespacedKey key = new NamespacedKey(plugin, "rp_smoke_bomb");
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);
        Player spy = event.getPlayer();
        Location loc = spy.getLocation();

        // Визуальные эффекты густого дыма
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 200, 3.0, 1.0, 3.0, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CANDLE_EXTINGUISH, 1.0f, 0.5f);

        // Накладываем эффекты ослепления на врагов в радиусе
        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 6.0, 4.0, 6.0);
        for (Entity entity : nearby) {
            if (entity instanceof Player target) {
                if (!target.equals(spy)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 2));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    target.sendMessage("§c§oВас ослепил густой дым!");
                }
            }
        }

        // Даем шпиону бафф скорости для побега
        spy.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

        // Корректно тратим 1 предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            spy.getInventory().setItemInMainHand(null);
        }
        spy.sendMessage("§8Взрыв! Вы использовали Слепой Порошок.");
    }
}