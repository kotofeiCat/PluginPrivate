package ru.politaboba.serverSystem.item.equipment.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.*;

public class WindCatcherArmorListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey templateKey;
    private final NamespacedKey armorMarkKey;

    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    // ИСПРАВЛЕНО: Кэш-список игроков, на которых сейчас надет полный комплект брони
    private final Set<UUID> activeWindCatchers = new HashSet<>();

    public WindCatcherArmorListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.templateKey = new NamespacedKey(plugin, "wind_catcher_template");
        this.armorMarkKey = new NamespacedKey(plugin, "wind_catcher_piece");

        startFlightControllerTask();
    }

    // НОВОЕ: Очистка памяти при выходе игрока с сервера
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        dashCooldowns.remove(uuid);
        activeWindCatchers.remove(uuid);
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack template = event.getInventory().getItem(0);
        ItemStack armor = event.getInventory().getItem(1);
        ItemStack ingredient = event.getInventory().getItem(2);

        if (template == null || armor == null || armor.getType() == Material.AIR) return;

        if (template.hasItemMeta() && template.getItemMeta().getPersistentDataContainer().has(templateKey, PersistentDataType.BYTE)) {
            if (ingredient == null || ingredient.getType() != Material.DIAMOND) {
                event.setResult(null);
                return;
            }

            ItemStack result = event.getResult() == null ? armor.clone() : event.getResult();
            ItemMeta meta = result.getItemMeta();
            if (meta != null && !meta.getPersistentDataContainer().has(armorMarkKey, PersistentDataType.BYTE)) {

                Material type = result.getType();
                EquipmentSlotGroup slotGroup = convertMaterialToSlotGroup(type);
                if (slotGroup == null) return;

                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                lore.add("§b§l🧭 Экипировка Ловца Ветров");
                lore.add(" §7• Облегченный вес: §a+3% к скорости");
                meta.setLore(lore);

                // ИСПРАВЛЕНО: Возвращаем ванильные дефолтные атрибуты защиты, иначе Spigot сотрет их при наложении кастомных
                applyVanillaAttributes(type, meta, slotGroup);

                NamespacedKey speedKey = new NamespacedKey(plugin, "wind_spd_" + type.name().toLowerCase());
                meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                        speedKey, 0.003, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

                meta.getPersistentDataContainer().set(armorMarkKey, PersistentDataType.BYTE, (byte) 1);
                result.setItemMeta(meta);
                event.setResult(result);
            }
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && activeWindCatchers.contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.4, 0.1, 0.4, 0.02);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_LAND, 0.8f, 1.3f);
            }
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        if (activeWindCatchers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);

            long now = System.currentTimeMillis();
            if (dashCooldowns.containsKey(player.getUniqueId()) && dashCooldowns.get(player.getUniqueId()) > now) {
                long left = (dashCooldowns.get(player.getUniqueId()) - now) / 1000L;
                player.sendMessage("§c§oРывок ветра еще перезаряжается! Осталось: " + left + " сек.");
                return;
            }

            Vector dashVelocity = player.getLocation().getDirection().multiply(1.3).setY(0.75);
            player.setVelocity(dashVelocity);

            player.getWorld().spawnParticle(Particle.SNEEZE, player.getLocation(), 5, 0.2, 0.2, 0.2, 0.1);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.3, 0.2, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.1f);

            dashCooldowns.put(player.getUniqueId(), now + 7000L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // ИСПРАВЛЕНО: Быстрая проверка через O(1) хэш-сет вместо циклического перебора метаданных брони при каждом шаге
        if (player.isOnGround() && activeWindCatchers.contains(player.getUniqueId()) && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    private void startFlightControllerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

                    if (hasFullSet(player)) {
                        activeWindCatchers.add(player.getUniqueId());
                        if (player.isOnGround() && !player.getAllowFlight()) {
                            player.setAllowFlight(true);
                        }
                    } else {
                        activeWindCatchers.remove(player.getUniqueId());
                        if (player.getAllowFlight()) {
                            player.setAllowFlight(false);
                            player.setFlying(false);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean hasFullSet(Player player) {
        int count = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR && piece.hasItemMeta()) {
                if (piece.getItemMeta().getPersistentDataContainer().has(armorMarkKey, PersistentDataType.BYTE)) {
                    count++;
                }
            }
        }
        return count == 4;
    }

    private void applyVanillaAttributes(Material type, ItemMeta meta, EquipmentSlotGroup slotGroup) {
        double armor = 0;
        double toughness = 0;
        double kbRes = 0;
        String name = type.name();

        if (name.startsWith("DIAMOND_")) {
            toughness = 2.0;
            if (name.endsWith("HELMET")) armor = 3.0;
            else if (name.endsWith("CHESTPLATE")) armor = 8.0;
            else if (name.endsWith("LEGGINGS")) armor = 6.0;
            else if (name.endsWith("BOOTS")) armor = 3.0;
        } else if (name.startsWith("NETHERITE_")) {
            toughness = 3.0;
            kbRes = 0.1;
            if (name.endsWith("HELMET")) armor = 3.0;
            else if (name.endsWith("CHESTPLATE")) armor = 8.0;
            else if (name.endsWith("LEGGINGS")) armor = 6.0;
            else if (name.endsWith("BOOTS")) armor = 3.0;
        }
        if (armor > 0) meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(plugin, "vanilla_arm_" + name.toLowerCase()), armor, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        if (toughness > 0) meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(plugin, "vanilla_tgh_" + name.toLowerCase()), toughness, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        if (kbRes > 0) meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(plugin, "vanilla_kbr_" + name.toLowerCase()), kbRes, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
    }

    private EquipmentSlotGroup convertMaterialToSlotGroup(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET")) return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return null;
    }
}