package ru.politaboba.serverSystem.item.cooking;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CampfireCookingListener implements Listener {

    private final ServerSystem plugin;
    private final Map<Location, CookingTask> activeTimers = new HashMap<>();

    public CampfireCookingListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || (block.getType() != Material.CAMPFIRE && block.getType() != Material.SOUL_CAMPFIRE)) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // 1. ЕСЛИ КЛИКНУЛИ ПУСТОЙ РУКОЙ — СНИМАЕМ С СЕКУНДОМЕРА
        if (hand.getType() == Material.AIR) {
            if (activeTimers.containsKey(loc)) {
                event.setCancelled(true);
                CookingTask task = activeTimers.remove(loc);
                task.finishCooking(player, loc);
            }
            return;
        }

        // 2. ЕСЛИ КЛИКНУЛИ НАШИМ ИНГРЕДИЕНТОМ — НАЧИНАЕМ ГОТОВКУ
        if (!hand.hasItemMeta()) return;
        NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
        String ingredientType = hand.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (ingredientType == null || ingredientType.equals("rp_mint_blend")) return;

        // Проверяем, свободен ли костер
        if (activeTimers.containsKey(loc)) {
            player.sendMessage("§cЭтот костер уже занят готовкой другого блюда!");
            event.setCancelled(true);
            return;
        }

        // Запускаем механику ручной прожарки
        event.setCancelled(true); // Отменяем ванильную анимацию костра, готовим через наш плагин

        // Забираем 1 предмет из руки
        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        // Спавним голограмму таймера над костром
        Location standLoc = loc.clone().add(0.5, 0.8, 0.5);
        ArmorStand stand = loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setCustomNameVisible(true);
            s.setCustomName("§7Запуск...");
        });

        CookingTask task = new CookingTask(stand, ingredientType);
        activeTimers.put(loc, task);
        task.runTaskTimer(plugin, 0L, 20L); // Тикает раз в секунду
        player.sendMessage("§e[Готовка] Вы выложили заготовку на угли. Следите за секундомером!");
        loc.getWorld().playSound(loc, Sound.BLOCK_CAMPFIRE_CRACKLE, 1.0f, 1.0f);
    }

    // Внутренний класс таски таймера готовки
    private class CookingTask extends BukkitRunnable {
        private final ArmorStand hologram;
        private final String type;
        private int seconds = 0;

        public CookingTask(ArmorStand hologram, String type) {
            this.hologram = hologram;
            this.type = type;
        }

        @Override
        public void run() {
            seconds++;

            // Проверка: если костер потушили во время готовки
            if (hologram.getLocation().getBlock().getType() == Material.AIR) {
                hologram.remove();
                this.cancel();
                return;
            }

            if (seconds < 10) {
                hologram.setCustomName("§7Прожарка: §e" + seconds + "с §7[§6Сырое§7]");
                hologram.getLocation().getWorld().playSound(hologram.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.2f, 1.5f);
            } else if (seconds <= 14) {
                hologram.setCustomName("§a§lГОТОВО! СНИМАЙ: §e" + seconds + "с §7[§aИдеально§7]");
                hologram.getLocation().getWorld().playSound(hologram.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            } else {
                hologram.setCustomName("§c§lПЕРЕЖАРЕНО! §7" + seconds + "с [§4Угли§7]");
                hologram.getLocation().getWorld().playSound(hologram.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.5f);
            }

            // Авто-сгорание через 20 секунд, если никто не забрал
            if (seconds >= 20) {
                hologram.remove();
                this.cancel();
            }
        }

        public void finishCooking(Player player, Location loc) {
            this.cancel();
            hologram.remove();

            ItemStack result;
            loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);

            // Логика определения качества прожарки
            if (seconds < 10) {
                player.sendMessage("§cВы поторопились! Получилось полусырое несъедобное мясо.");
                result = createFailedDish(Material.ROTTEN_FLESH, "§cНедожаренный кусок пищи", "raw_ruined");
            } else if (seconds <= 14) {
                player.sendMessage("§a§lВеликолепно! Блюдо приготовлено шеф-поваром.");
                result = convertToPerfectDish();
            } else {
                player.sendMessage("§4Еда полностью сгорела на углях!");
                result = createFailedDish(Material.CHARCOAL, "§8Сгоревшие угольные остатки", "burnt_ruined");
            }

            // Выдаем результат игроку
            if (!player.getInventory().addItem(result).isEmpty()) {
                loc.getWorld().dropItemNaturally(loc, result);
            }
        }

        private ItemStack convertToPerfectDish() {
            // Превращаем сырой ID заготовки в финальный ID блюда
            switch (type) {
                case "rp_raw_sprinter":
                    return createDish(Material.COOKED_BEEF, "§dСтейк «Горный Спринтер»", "sprinter_steak",
                            List.of("§7Идеально прожаренное мясо со специями.", "", "§⚡ Эффект (8 минут):", " §e• Скорость II", " §e• Прыгучесть I"));
                case "rp_raw_kebab":
                    return createDish(Material.COOKED_CHICKEN, "§6РП-Шашлык на углях", "kebab",
                            List.of("§7Ароматные сочные куски мяса на шампуре.", "", "§⚡ Эффект (10 минут):", " §e• +2 Золотых сердца"));
                case "rp_raw_carp":
                    return createDish(Material.COOKED_COD, "§eИмператорский Запеченный Карп", "royal_carp",
                            List.of("§7Рыба с хрустящей золотистой корочкой.", "", "§⚡ Эффект (15 минут):", " §e• Подводное дыхание + Скорость"));
                default:
                    return new ItemStack(Material.PORKCHOP);
            }
        }

        private ItemStack createDish(Material mat, String name, String nbtValue, List<String> lore) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, nbtValue);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createFailedDish(Material mat, String name, String nbtValue) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(List.of("§7Результат неудачной кулинарии. Побочные эффекты."));
            NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, nbtValue);
            item.setItemMeta(meta);
            return item;
        }
    }
}