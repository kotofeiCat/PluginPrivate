package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

public class StormBowListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey bowKey;
    private final NamespacedKey arrowKey;

    public StormBowListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.bowKey = new NamespacedKey(plugin, "rp_storm_bow");
        this.arrowKey = new NamespacedKey(plugin, "storm_arrow");
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack bow = event.getBow();
        if (bow == null || !bow.hasItemMeta()) return;

        // Проверяем наличие системной NBT-метки нашего Лука Бури
        if (!bow.getItemMeta().getPersistentDataContainer().has(bowKey, PersistentDataType.BYTE)) return;

        // Контроль баланса: добавляем кулдаун на использование лука (4 секунды = 80 тиков),
        // чтобы игроки не могли спамить молниями без натяжения тетивы
        if (player.hasCooldown(Material.BOW)) {
            player.sendActionBar("§c§oНебесный гнев еще перезаряжается!");
            event.setCancelled(true);
            return;
        }

        // Маркируем выпущенную стрелу
        if (event.getProjectile() instanceof Arrow arrow) {
            arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
            player.setCooldown(Material.BOW, 80); // Установка ванильного кулдауна на предмет
        }
    }

    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Проверяем, является ли стрела "громовой"
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;

        if (arrow.getShooter() instanceof Player shooter) {
            // strikeLightningEffect создает молнию БЕЗ поджога блоков и уничтожения выпавших вещей (безопасно для RP)
            target.getWorld().strikeLightningEffect(target.getLocation());

            // Дополнительный базовый урон от молнии (14.0 единиц = 7 полных сердец).
            // Добавляем к текущему урону события, чтобы броня цели могла его частично поглотить
            double additionalDamage = 14.0;
            event.setDamage(event.getDamage() + additionalDamage);

            shooter.sendMessage("§b⚡ §lГромовой разряд! §bМолния поразила цель " + target.getName() + "!");
        }
    }
}