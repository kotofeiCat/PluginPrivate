package ru.politaboba.serverSystem.item.food.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.item.food.model.CustomDish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CampfireCookingListener implements Listener {

    private final ServerSystem plugin;
    final Map<Location, CookingTask> activeTimers = new HashMap<>();

    public CampfireCookingListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    // ИСПРАВЛЕНО: Аварийная очистка голограмм костров при перезагрузке плагина
    public void cleanup() {
        for (CookingTask task : activeTimers.values()) {
            task.abortCooking(false);
        }
        activeTimers.clear();
    }

    @EventHandler
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.CAMPFIRE && block.getType() != Material.SOUL_CAMPFIRE)) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR) {
            if (activeTimers.containsKey(loc)) {
                event.setCancelled(true);
                CookingTask task = activeTimers.remove(loc);
                task.finishCooking(player);
            }
            return;
        }

        if (!hand.hasItemMeta()) return;
        NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
        String ingredientType = hand.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (ingredientType == null || ingredientType.equals("rp_mint_blend")) return;

        if (activeTimers.containsKey(loc)) {
            player.sendMessage("§cЭтот костер уже занят готовкой другого блюда!");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        ItemStack rawCopy = hand.clone();
        rawCopy.setAmount(1);

        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        Location standLoc = loc.clone().add(0.5, 0.8, 0.5);
        ArmorStand stand = loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setCustomNameVisible(true);
            s.setCustomName("§7Запуск...");
        });

        CookingTask task = new CookingTask(loc, stand, ingredientType, rawCopy);
        activeTimers.put(loc, task);
        task.runTaskTimer(plugin, 0L, 20L);

        player.sendMessage("§e[Готовка] Вы выложили заготовку на угли. Следите за секундомером!");
        loc.getWorld().playSound(loc, Sound.BLOCK_CAMPFIRE_CRACKLE, 1.0f, 1.0f);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (activeTimers.containsKey(loc)) {
            CookingTask task = activeTimers.remove(loc);
            task.abortCooking(true);
            event.getPlayer().sendMessage("§cГотовка прервана: костер был уничтожен!");
        }
    }

    private class CookingTask extends BukkitRunnable {
        private final Location loc;
        private final ArmorStand hologram;
        private final String type;
        private final ItemStack rawItem;
        private int seconds = 0;

        public CookingTask(Location loc, ArmorStand hologram, String type, ItemStack rawItem) {
            this.loc = loc;
            this.hologram = hologram;
            this.type = type;
            this.rawItem = rawItem;
        }

        @Override
        public void run() {
            seconds++;

            if (loc.getBlock().getType() != Material.CAMPFIRE && loc.getBlock().getType() != Material.SOUL_CAMPFIRE) {
                activeTimers.remove(loc);
                abortCooking(true);
                return;
            }

            if (seconds < 10) {
                hologram.setCustomName("§7Прожарка: §e" + seconds + "с §7[§6Сырое§7]");
                loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 0.2f, 1.5f);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 3, 0.1, 0.1, 0.1, 0.01);
            }
            else if (seconds <= 14) {
                hologram.setCustomName("§a§lГОТОВО! СНИМАЙ: §e" + seconds + "с §7[§aИдеально§7]");
                loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.2f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.6, 0.5), 4, 0.2, 0.1, 0.2, 0.1);
            }
            else {
                hologram.setCustomName("§c§lПЕРЕЖАРЕНО! §7" + seconds + "с [§4Угли§7]");
                loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.5f);
                loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, 0.7, 0.5), 5, 0.1, 0.2, 0.1, 0.02);
            }

            if (seconds >= 20) {
                activeTimers.remove(loc);
                abortCooking(false);

                ItemStack burntResult = createFailedDish(Material.CHARCOAL, "§8Сгоревшие угольные остатки", "burnt_ruined");
                loc.getWorld().dropItemNaturally(loc, burntResult);
                loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
            }
        }

        public void finishCooking(Player player) {
            this.cancel();
            if (hologram.isValid()) hologram.remove();

            ItemStack result;
            loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);

            if (seconds < 10) {
                player.sendMessage("§cВы поторопились! Получилось полусырое несъедобное мясо.");
                result = plugin.getFoodManager().getDishById("raw_ruined").createCookedItem(plugin);
            }
            else if (seconds <= 14) {
                CustomDish dish = plugin.getFoodManager().getDishByRawId(type);
                if (dish != null) {
                    player.sendMessage("§a§lВеликолепно! Блюдо приготовлено шеф-поваром.");
                    result = dish.createCookedItem(plugin);
                } else {
                    result = new ItemStack(Material.PORKCHOP);
                }
            }
            else {
                player.sendMessage("§4Еда полностью сгорела на углях!");
                result = plugin.getFoodManager().getDishById("burnt_ruined").createCookedItem(plugin);
            }

            if (!player.getInventory().addItem(result).isEmpty()) {
                loc.getWorld().dropItemNaturally(loc, result);
            }
        }

        public void abortCooking(boolean dropIngredient) {
            this.cancel();
            if (hologram.isValid()) hologram.remove();
            if (dropIngredient && rawItem != null) {
                loc.getWorld().dropItemNaturally(loc, rawItem);
            }
        }

        private ItemStack createFailedDish(Material mat, String name, String nbtValue) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(List.of("§7Результат неудачной кулинарии. Побочные эффекты."));
                NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, nbtValue);
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}