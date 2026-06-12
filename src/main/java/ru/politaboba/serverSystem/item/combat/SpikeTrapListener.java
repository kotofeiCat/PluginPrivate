package ru.politaboba.serverSystem.item.combat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.Collection;

public class SpikeTrapListener implements Listener {

    private final ServerSystem plugin;

    public SpikeTrapListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlaceTrap(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        NamespacedKey key = new NamespacedKey(plugin, "rp_spike_trap");
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);
        Player engineer = event.getPlayer();

        // Получаем точку установки чуть выше кликнутого блока
        Location trapLoc = event.getClickedBlock().getLocation().add(0.5, 0.0, 0.5);

        // Спавним невидимый маркер ловушки через ArmorStand
        ArmorStand trapSign = trapLoc.getWorld().spawn(trapLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true); // Чтобы не мешал проходу и хитбоксам
            stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_spike_trap"), PersistentDataType.BYTE, (byte) 1);
        });

        trapLoc.getWorld().playSound(trapLoc, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.5f);
        trapLoc.getWorld().playSound(trapLoc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);
        engineer.sendMessage("§6[Ловушка] Осколочный ёж установлен и взведён!");

        // Тратим предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            engineer.getInventory().setItemInMainHand(null);
        }

        // Запускаем циклическую задачу проверки детонации (каждые 5 тиков = 0.25 сек)
        new BukkitRunnable() {
            int lifetime = 1200; // Ловушка уничтожится сама через 5 минут (1200 проверок), если не взорвется

            @Override
            public void run() {
                lifetime--;
                if (!trapSign.isValid() || lifetime <= 0) {
                    this.cancel();
                    if (trapSign.isValid()) trapSign.remove();
                    return;
                }

                // Спавним редкие искры, чтобы внимательный враг мог заметить мину на земле
                if (lifetime % 8 == 0) {
                    trapLoc.getWorld().spawnParticle(Particle.CRIT, trapLoc.clone().add(0, 0.1, 0), 1, 0.1, 0.0, 0.1, 0.01);
                }

                // Ищем существ в радиусе 1.8 блоков вокруг ловушки
                Collection<Entity> nearby = trapLoc.getWorld().getNearbyEntities(trapLoc, 1.8, 1.5, 1.8);
                for (Entity entity : nearby) {
                    if (entity instanceof LivingEntity target && !target.equals(engineer)) {
                        // Если это игрок-союзник (или сам создатель), игнорируем.
                        // Но если это враг или монстр — взрываем!
                        if (target instanceof Player targetPlayer && targetPlayer.getGameMode().name().equals("SPECTATOR")) continue;

                        this.cancel(); // Останавливаем таймер
                        detonate(trapSign, trapLoc, engineer);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L); // Активация через 1 секунду после установки, проверка каждые 5 тиков
    }

    private void detonate(ArmorStand stand, Location loc, Player creator) {
        stand.remove();

        // Эффекты разлетающихся осколков
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.1, 0.1, 0.1, 0.05);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 0.2, 0), 60, 1.5, 0.5, 1.5, 0.2);
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.5f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);

        // Поражение целей в радиусе взрыва (3.5 блока)
        Collection<Entity> entities = loc.getWorld().getNearbyEntities(loc, 3.5, 2.0, 3.5);
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity target && !target.equals(creator)) {
                // Наносим чистый урон осколков
                target.damage(6.0, creator); // 6 единиц урона = 3 сердца

                // Травма ног: жесткое замедление V на 4 секунды (80 тиков)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, true, true));

                if (target instanceof Player p) {
                    p.sendMessage("§c§l💥 ВЫ ПОДОРВАЛИСЬ НА ОСКОЛОЧНОМ ЕЖЕ! §cВаши ноги сильно повреждены осколками.");
                }
            }
        }
        creator.sendMessage("§a§l[Ловушка] Ваш Осколочный ёж успешно сдетонировал!");
    }
}