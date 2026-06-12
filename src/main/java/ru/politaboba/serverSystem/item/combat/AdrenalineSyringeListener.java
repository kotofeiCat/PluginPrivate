package ru.politaboba.serverSystem.item.combat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

public class AdrenalineSyringeListener implements Listener {

    private final ServerSystem plugin;

    public AdrenalineSyringeListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUseSyringe(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        NamespacedKey key = new NamespacedKey(plugin, "rp_adrenaline_syringe");
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Проверка ванильного кулдауна, чтобы не прожимали шприцы один за другим
        if (player.hasCooldown(item.getType())) {
            player.sendMessage("§cВаше сердце еще не восстановилось после предыдущей инъекции!");
            return;
        }

        // Ставим кулдаун на предмет (например, 40 секунд = 800 тиков)
        player.setCooldown(item.getType(), 800);

        Location loc = player.getLocation();

        // Визуальные эффекты: капли крови/адреналина и резкий звук инъекции
        loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc.add(0, 1, 0), 15, 0.2, 0.3, 0.2, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_INFECT, 1.0f, 1.8f);
        loc.getWorld().playSound(loc, Sound.ITEM_BOTTLE_EMPTY, 0.8f, 0.5f);

        // Удаляем негативные эффекты замедления перед рывком
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        // Накладываем мощные боевые баффы на 10 секунд (200 тиков)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, false, true, true)); // Скорость III
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true, true)); // Сила I
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, false, true, true)); // Регенерация II

        player.sendMessage("§4§l⚡ АДРЕНАЛИН! §fВы чувствуете безумный прилив сил, боль отступает!");

        // Корректно тратим 1 предмет
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Запускаем таймер отката (эффект истощения через 10 секунд)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendMessage("§c§oДействие стимулятора закончилось. Ваше тело сковала жуткая усталость...");
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 1)); // Замедление II на 8 секунд
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 0)); // Слабость I на 8 секунд
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.8f);
                }
            }
        }.runTaskLater(plugin, 200L); // 200 тиков = 10 секунд
    }
}