package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZephyrSpearListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey spearKey;

    // Карта для отслеживания брошенных копий: UUID Трезубца -> Сам ItemStack копья
    private final Map<UUID, ItemStack> thrownSpears = new HashMap<>();

    // НОВОЕ: Карта кулдаунов для броска (UUID игрока -> Время окончания КД в мс)
    private final Map<UUID, Long> throwCooldowns = new HashMap<>();
    private final long COOLDOWN_TIME = 15000L; // Кулдаун броска: 15000 миллисекунд (15 секунд)

    public ZephyrSpearListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.spearKey = new NamespacedKey(plugin, "rp_zephyr_spear");
    }

    // НОВОЕ: Очистка памяти при выходе игрока с сервера
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        throwCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSpearHit(EntityDamageByEntityEvent event) {
        // 1. МЕХАНИКА БЛИЖНЕГО БОЯ (Подброс при крите — работает на игроках и на МОБАХ)
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() != Material.AIR && hand.hasItemMeta() &&
                    hand.getItemMeta().getPersistentDataContainer().has(spearKey, PersistentDataType.BYTE)) {

                boolean isCritical = player.getFallDistance() > 0.0F
                        && !player.isOnGround()
                        && !player.isInsideVehicle()
                        && !player.hasPotionEffect(PotionEffectType.BLINDNESS);

                if (isCritical) {
                    // ИСПРАВЛЕНО: Запускаем подброс через 1 тик, чтобы ванильный урон не сбивал вектор Y
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (target.isValid()) {
                            // Значение 1.35 мощно подкидывает цель вверх примерно на 5-6 блоков
                            target.setVelocity(new Vector(0, 1.35, 0));
                        }
                    }, 1L);

                    target.getWorld().spawnParticle(Particle.RAIN, target.getLocation().add(0, 0.5, 0), 15, 0.2, 0.4, 0.2, 0.15);
                    target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 10, 0.3, 0.1, 0.3, 0.01);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 0.9f);
                }
            }
        }

        // 2. МЕХАНИКА ДАЛЬНЕГО БОЯ (Прямое попадание брошенным копьем в цель)
        if (event.getDamager() instanceof Trident trident) {
            if (thrownSpears.containsKey(trident.getUniqueId())) {
                if (event.getEntity() instanceof LivingEntity target) {

                    // НОВОЕ: Увеличиваем урон от броска на 75% по сравнению с обычным базовым ударом оружия
                    event.setDamage(event.getDamage() * 1.75);

                    // ИСПРАВЛЕНО: Подброс от броска снаряда также перенесен на 1 тик позже (высота около 3 блоков)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (target.isValid()) {
                            target.setVelocity(new Vector(0, 1.0, 0));
                        }
                    }, 1L);

                    target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.3f);
                }
            }
        }
    }

    @EventHandler
    public void onSpearThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR || !hand.hasItemMeta()) return;
        if (!hand.getItemMeta().getPersistentDataContainer().has(spearKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        // НОВОЕ: Проверка кулдауна на бросок
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (throwCooldowns.containsKey(uuid) && throwCooldowns.get(uuid) > now) {
            long timeLeft = (throwCooldowns.get(uuid) - now) / 1000L;
            player.sendActionBar("§c§oБросок копья перезаряжается! Осталось: " + timeLeft + " сек.");
            return;
        }

        ItemStack spearToThrow = hand.clone();
        spearToThrow.setAmount(1);

        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        Trident trident = player.launchProjectile(Trident.class);
        trident.setItemStack(spearToThrow);

        thrownSpears.put(trident.getUniqueId(), spearToThrow);

        // НОВОЕ: Запись времени окончания перезарядки
        throwCooldowns.put(uuid, now + COOLDOWN_TIME);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);

        // Страховочный возврат, если трезубец улетел слишком далеко или завис в воздухе
        new BukkitRunnable() {
            @Override
            public void run() {
                if (trident.isValid() && thrownSpears.containsKey(trident.getUniqueId())) {
                    startReturnTask(trident, player);
                }
            }
        }.runTaskLater(plugin, 80L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!thrownSpears.containsKey(trident.getUniqueId())) return;

        if (trident.getShooter() instanceof Player player) {
            startReturnTask(trident, player);
        }
    }

    private void startReturnTask(Trident trident, Player player) {
        trident.setGravity(false);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Если снаряд уже удален или возвращен в инвентарь
                if (!trident.isValid() || !thrownSpears.containsKey(trident.getUniqueId())) {
                    cancel();
                    return;
                }

                // Защита от выхода игрока / смены миров
                if (!player.isOnline() || !trident.getWorld().equals(player.getWorld())) {
                    ItemStack item = thrownSpears.remove(trident.getUniqueId());
                    if (item != null) {
                        trident.getWorld().dropItemNaturally(trident.getLocation(), item);
                    }
                    trident.remove();
                    cancel();
                    return;
                }

                Location playerEyes = player.getEyeLocation();
                Location tridentLoc = trident.getLocation();
                double distance = tridentLoc.distance(playerEyes);

                if (distance < 1.5) {
                    ItemStack item = thrownSpears.remove(trident.getUniqueId());
                    if (item != null) {
                        Map<Integer, ItemStack> leftOver = player.getInventory().addItem(item);
                        if (!leftOver.isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                        }
                        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
                    }
                    trident.remove();
                    cancel();
                    return;
                }

                // Расчет вектора направления движения к игроку
                Vector direction = playerEyes.toVector().subtract(tridentLoc.toVector()).normalize();

                // Controlled teleportation (ignores getting stuck in blocks)
                Location nextLocation = tridentLoc.add(direction.multiply(1.30));
                nextLocation.setDirection(direction);
                trident.teleport(nextLocation);

                // Эффекты шлейфа воздушного потока
                trident.getWorld().spawnParticle(Particle.CLOUD, trident.getLocation(), 1, 0.0, 0.0, 0.0, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L); // Работает каждый тик
    }
}