package ru.politaboba.serverSystem.item.brewing;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.List;

public class BrewingStorageListener implements Listener {

    private final ServerSystem plugin;

    public BrewingStorageListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBrewingClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING) return;

        BrewerInventory inv = (BrewerInventory) event.getInventory();
        BrewingStand stand = inv.getHolder();
        if (stand == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack ingredient = inv.getIngredient();
                if (ingredient == null || ingredient.getType() == Material.AIR) return;

                if (!ingredient.hasItemMeta()) return;
                NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
                String type = ingredient.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

                if (type != null && type.equals("rp_mint_blend")) {
                    // Проверяем, есть ли снизу бутылочки с водой
                    boolean hasWater = false;
                    for (int i = 0; i < 3; i++) {
                        ItemStack pot = inv.getItem(i);
                        if (pot != null && pot.getType() == Material.POTION) {
                            hasWater = true;
                        }
                    }

                    if (hasWater && stand.getFuelLevel() > 0) {
                        // БЛОКИРУЕМ ВАНИЛЬНУЮ ВАРКУ (чтобы не сварилось ванильное зелье слабости)
                        stand.setBrewingTime(0);

                        startTeaBrewing(stand, inv);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private void startTeaBrewing(BrewingStand stand, BrewerInventory inv) {
        // Имитируем ванильную варку чая
        new BukkitRunnable() {
            int ticks = 5; // Время варки — 5 секунд

            @Override
            public void run() {
                if (inv.getIngredient() == null || inv.getIngredient().getType() == Material.AIR) {
                    this.cancel();
                    return;
                }

                ticks--;
                stand.getLocation().getWorld().playSound(stand.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 1.2f);

                if (ticks <= 0) {
                    this.cancel();

                    // Тратим 1 ингредиент сверху
                    ItemStack ing = inv.getIngredient();
                    ing.setAmount(ing.getAmount() - 1);

                    // Превращаем нижние колбы в Целебный Чай
                    for (int i = 0; i < 3; i++) {
                        ItemStack pot = inv.getItem(i);
                        if (pot != null && pot.getType() == Material.POTION) {
                            inv.setItem(i, createTeaItem());
                        }
                    }
                    stand.getLocation().getWorld().playSound(stand.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private ItemStack createTeaItem() {
        ItemStack tea = new ItemStack(Material.POTION);
        ItemMeta meta = tea.getItemMeta();
        meta.setDisplayName("§aЦелебный Мятный Чай");
        meta.setLore(List.of("§7Напиток заварен профессиональным поваром в стойке.", "", "§⚡ Эффект (10 минут):", " §e• Регенерация I"));

        NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "mint_tea");
        tea.setItemMeta(meta);
        return tea;
    }
}