package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.*;

public class MercenaryArmorListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey armorMercMarkKey;

    private final Set<UUID> invisibleMercenaries = new HashSet<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Integer> standSeconds = new HashMap<>();
    private final Map<UUID, Long> camoCooldowns = new HashMap<>();
    private final Map<UUID, Long> camoExpirations = new HashMap<>(); // Таймер на 3 минуты

    public MercenaryArmorListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.armorMercMarkKey = new NamespacedKey(plugin, "mercenary_armor_piece");

        startArmorUpdateTask();
    }

    private void startArmorUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    int mercenaryPieces = 0;

                    for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
                        if (armorPiece != null && armorPiece.getType() != Material.AIR && armorPiece.hasItemMeta()) {
                            if (armorPiece.getItemMeta().getPersistentDataContainer().has(armorMercMarkKey, PersistentDataType.BYTE)) {
                                mercenaryPieces++;
                            }
                        }
                    }

                    if (mercenaryPieces == 4) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true));

                        if (invisibleMercenaries.contains(uuid)) {
                            // ИСПРАВЛЕНО: Проверка окончания 3-х минут действия камуфляжа
                            long now = System.currentTimeMillis();
                            long expireTime = camoExpirations.getOrDefault(uuid, 0L);

                            if (now >= expireTime) {
                                revealMercenary(player, "Время маскировки истекло");
                            } else {
                                // ИСПРАВЛЕНО: Вывод красивого таймера оставшегося времени в Action Bar
                                long remainingSecs = (expireTime - now) / 1000;
                                long mins = remainingSecs / 60;
                                long secs = remainingSecs % 60;
                                player.sendActionBar(String.format("§8[Камуфляж] §aМаскировка активна §7| §eОсталось: %02d:%02d", mins, secs));

                                // Поддерживаем невидимость для самого игрока (чтобы видел свои прозрачные руки)
                                if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, false));
                                }
                            }
                        } else {
                            long now = System.currentTimeMillis();
                            if (camoCooldowns.getOrDefault(uuid, 0L) > now) {
                                long remainingCd = (camoCooldowns.get(uuid) - now) / 1000;
                                if (player.isSneaking()) {
                                    player.sendActionBar("§8[Камуфляж] §cПерезарядка: " + remainingCd + "с.");
                                }
                                continue;
                            }

                            // Вход в режим маскировки (требуется посидеть 3 секунды смирно)
                            if (player.isSneaking()) {
                                Location lastLoc = lastLocations.get(uuid);
                                if (lastLoc != null && player.getLocation().distanceSquared(lastLoc) <= 0.01) {
                                    int seconds = standSeconds.getOrDefault(uuid, 0) + 1;
                                    standSeconds.put(uuid, seconds);

                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.5f);
                                    player.sendActionBar("§8[Камуфляж] §eВход в скрытность через: " + (3 - seconds) + "с.");

                                    if (seconds >= 3) {
                                        vanishMercenary(player);
                                    }
                                } else {
                                    standSeconds.put(uuid, 0);
                                    lastLocations.put(uuid, player.getLocation().clone());
                                }
                            } else {
                                standSeconds.put(uuid, 0);
                            }
                        }
                    } else {
                        if (invisibleMercenaries.contains(uuid)) {
                            revealMercenary(player, "Снят комплект маскировки");
                        }
                        standSeconds.remove(uuid);
                        lastLocations.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ИСПРАВЛЕНО: Удалены слушатели PlayerMoveEvent и PlayerToggleSneakEvent, чтобы разрешить свободный бег и ходьбу!

    @EventHandler
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (invisibleMercenaries.contains(player.getUniqueId())) {
                revealMercenary(player, "Получен урон");
            }
        }

        if (event.getDamager() instanceof Player player) {
            if (invisibleMercenaries.contains(player.getUniqueId())) {
                revealMercenary(player, "Атака из засады");

                event.setDamage(event.getDamage() * 1.25);
                player.sendMessage("§4⚔ Внезапная атака из засады! Урон увеличен на 25%.");
                player.getWorld().spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.2);
            }
        }
    }

    // ИСПРАВЛЕНО: Мобы теперь полностью игнорируют невидимого игрока
    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            if (invisibleMercenaries.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    private void vanishMercenary(Player player) {
        UUID uuid = player.getUniqueId();
        invisibleMercenaries.add(uuid);
        standSeconds.put(uuid, 0);
        // ИСПРАВЛЕНО: Установка лимита времени на 3 минуты (3 * 60 * 1000 мс)
        camoExpirations.put(uuid, System.currentTimeMillis() + (3 * 60 * 1000L));

        // Скрываем игрока, его броню и предметы для ОСТАЛЬНЫХ
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }

        // Даем эффект невидимости для рук САМОГО игрока
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 3600, 0, false, false, false));

        player.sendMessage("§8[Камуфляж] §aВы растворились в окружении на 3 минуты! Можно свободно передвигаться.");
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 25, 0.2, 0.4, 0.2, 0.01);
    }

    private void revealMercenary(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        invisibleMercenaries.remove(uuid);
        standSeconds.put(uuid, 0);
        camoExpirations.remove(uuid);
        camoCooldowns.put(uuid, System.currentTimeMillis() + 15000L); // Кулдаун 15 секунд

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, player);
        }

        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        player.sendMessage("§8[Камуфляж] §cРежим маскировки отключен (" + reason + "). Перезарядка 15с.");
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 15, 0.2, 0.3, 0.2, 0.05);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID vanishedUuid : invisibleMercenaries) {
            Player vanished = Bukkit.getPlayer(vanishedUuid);
            if (vanished != null && vanished.isOnline()) {
                joined.hidePlayer(plugin, vanished);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (invisibleMercenaries.contains(player.getUniqueId())) {
            revealMercenary(player, "Выход из игры");
        }
        lastLocations.remove(player.getUniqueId());
        standSeconds.remove(player.getUniqueId());
        camoCooldowns.remove(player.getUniqueId());
        camoExpirations.remove(player.getUniqueId());
    }
}