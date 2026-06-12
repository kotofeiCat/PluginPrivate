package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.HashSet;
import java.util.Set;

public class CastleBreakerAxeListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey axeKey;

    public CastleBreakerAxeListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.axeKey = new NamespacedKey(plugin, "artifact_axe");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DIAMOND_AXE) return;
        if (!item.hasItemMeta()) return;

        if (!item.getItemMeta().getPersistentDataContainer().has(axeKey, PersistentDataType.BYTE)) return;

        Player player = event.getPlayer();

        if (player.hasCooldown(Material.DIAMOND_AXE)) return;

        // Кулдаун повышен до 60 секунд (1200 тиков) из-за возросшей силы способности
        player.setCooldown(Material.DIAMOND_AXE, 1200);

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().setY(0).normalize();
        Location startLoc = player.getLocation().clone();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.7f);

        // Множество, чтобы не накладывать эффекты на одну цель дважды за одну волну
        Set<LivingEntity> affectedTargets = new HashSet<>();

        // Запускаем красивую цепочку челюстей с задержкой, чтобы они вылезали «волной» по очереди
        for (int i = 0; i < 7; i++) {
            final int step = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;

                    // Рассчитываем точку для текущего шага волны (каждые 1.5 блока)
                    Location fangLoc = startLoc.clone().add(direction.clone().multiply(1.5 * (step + 1)));

                    // Улучшенная адаптация под рельеф (ищем пол)
                    Block highestBlock = fangLoc.getWorld().getHighestBlockAt(fangLoc);
                    fangLoc.setY(highestBlock.getY() + 1.0);

                    // Спавним челюсти
                    fangLoc.getWorld().spawn(fangLoc, EvokerFangs.class, fangs -> {
                        fangs.setOwner(player);
                    });

                    // Звуковой шлейф раскалывающегося камня
                    fangLoc.getWorld().playSound(fangLoc, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.5f + (step * 0.1f));

                    // Поиск и обработка целей в радиусе удара челюстей
                    for (Entity nearby : fangLoc.getWorld().getNearbyEntities(fangLoc, 1.2, 2.0, 1.2)) {
                        if (nearby instanceof LivingEntity target && !target.equals(player)) {
                            if (affectedTargets.contains(target)) continue;
                            affectedTargets.add(target);

                            // Если цель — игрок, который блокирует удар щитом
                            if (target instanceof Player targetPlayer && targetPlayer.isBlocking()) {
                                // Ломаем щит (устанавливаем кулдаун на использование щита)
                                targetPlayer.setCooldown(Material.SHIELD, 100); // Отключение щита на 5 секунд
                                targetPlayer.clearActivePotionEffects();
                                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);
                                targetPlayer.sendMessage("§c§l💥 Ваш щит был расколот Топором Раскалывателя!");
                                player.sendMessage("§6§l⚔ Вы успешно пробили щит цели " + targetPlayer.getName() + "!");
                            }

                            // Накладываем эффекты оглушения/дезориентации (Замедление 4 и Тьма на 3 секунды)
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true, true));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, true, true));

                            // Дополнительный визуальный эффект «оглушения» над головой цели
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.5f);
                        }
                    }
                }
            }.runTaskLater(plugin, step * 2L); // Интервал в 2 тика между появлением челюстей создает эффект бегущей дорожки
        }
    }
}