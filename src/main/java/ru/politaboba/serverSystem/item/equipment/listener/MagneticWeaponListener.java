package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class MagneticWeaponListener implements Listener {

    private final NamespacedKey swordKey;

    public MagneticWeaponListener(ServerSystem plugin) {
        // Рекомендуется использовать BOOLEAN для простых меток "это тот самый предмет"
        this.swordKey = new NamespacedKey(plugin, "magnetic_greatsword");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Проверяем только правый клик
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return;

        // Безопасное извлечение метаданных
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        // ВАЖНО: Тип данных здесь должен СТРОГО совпадать с тем, что ты указываешь при создании меча.
        // Если при создании ты использовал BYTE, измени обратно на PersistentDataType.BYTE
        if (!container.has(swordKey, PersistentDataType.BYTE)) return;

        Player player = event.getPlayer();

        // Проверяем кулдаун Vanilla-системы
        if (player.hasCooldown(Material.NETHERITE_SWORD)) return;

        // Ищем сущности в радиусе
        List<LivingEntity> validTargets = new ArrayList<>();
        double sumX = 0, sumY = 0, sumZ = 0;

        for (Entity entity : player.getNearbyEntities(12.0, 8.0, 12.0)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (hasMagneticArmor(target)) {
                    validTargets.add(target);
                    sumX += target.getLocation().getX();
                    sumY += target.getLocation().getY();
                    sumZ += target.getLocation().getZ();
                }
            }
        }

        // ИСПРАВЛЕНИЕ: Сначала проверяем количество целей, и только потом делим!
        if (validTargets.size() < 2) {
            player.sendMessage("§c🧲 Поблизости нет достаточного количества целей в металлической броне!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_HIT, 0.5f, 1.5f);
            return;
        }

        // Теперь деление на validTargets.size() абсолютно безопасно, так как оно минимум равно 2
        Location centerLocation = new Location(
                player.getWorld(),
                sumX / validTargets.size(),
                sumY / validTargets.size(),
                sumZ / validTargets.size()
        );

        // Включаем долгий кулдаун только при успешном срабатывании
        player.setCooldown(Material.NETHERITE_SWORD, 240);

        // Эффекты в эпицентре
        centerLocation.getWorld().spawnParticle(Particle.PORTAL, centerLocation, 50, 0.5, 0.5, 0.5, 1.0);
        centerLocation.getWorld().spawnParticle(Particle.GUST, centerLocation, 3, 0.1, 0.1, 0.1, 0.0);
        centerLocation.getWorld().playSound(centerLocation, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.5f, 0.5f);
        centerLocation.getWorld().playSound(centerLocation, Sound.BLOCK_LODESTONE_BREAK, 1.2f, 0.8f);

        player.sendMessage("§d§l🧲 Магнитный коллапс! §fВы сковали металлические доспехи §e" + validTargets.size() + " §fврагов.");

        // Логика притяжения
        for (LivingEntity target : validTargets) {
            Location targetLoc = target.getLocation();

            Vector pullDirection = centerLocation.toVector().subtract(targetLoc.toVector());
            double distance = pullDirection.length();

            if (distance > 0.5) {
                pullDirection.normalize();

                double pullStrength = Math.min(1.8, 0.5 + (distance * 0.25));
                Vector velocity = pullDirection.multiply(pullStrength).setY(0.35);

                target.setVelocity(velocity);

                target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, targetLoc.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.01);
                target.damage(3.0, player);
            }
        }
    }

    private boolean hasMagneticArmor(LivingEntity entity) {
        if (entity.getEquipment() == null) return false;

        ItemStack[] armorContents = entity.getEquipment().getArmorContents();
        if (armorContents == null) return false;

        for (ItemStack armorPiece : armorContents) {
            if (armorPiece == null || armorPiece.getType() == Material.AIR) continue;

            String matName = armorPiece.getType().name();

            if (matName.startsWith("IRON_") || matName.startsWith("DIAMOND_") || matName.startsWith("NETHERITE_")) {
                return true;
            }
        }
        return false;
    }
}