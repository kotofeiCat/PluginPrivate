package ru.politaboba.serverSystem.item.brewing;

import org.bukkit.Location;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrewingStorageListener implements Listener {

    private final ServerSystem plugin;
    private final Set<Location> activeBrews = new HashSet<>();

    public BrewingStorageListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBrewingClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING) return;

        BrewerInventory inv = (BrewerInventory) event.getInventory();
        BrewingStand stand = inv.getHolder();
        if (stand == null) return;

        if (activeBrews.contains(stand.getLocation())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack ingredient = inv.getIngredient();
                if (ingredient == null || ingredient.getType() == Material.AIR) return;
                if (!ingredient.hasItemMeta()) return;

                NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
                String type = ingredient.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

                // Проверяем, что это один из наших 3-х алхимических ингредиентов
                if (type != null && (type.equals("rp_mint_blend") || type.equals("rp_cucumber_blend") || type.equals("rp_pine_gather"))) {
                    boolean hasWater = false;
                    for (int i = 0; i < 3; i++) {
                        ItemStack pot = inv.getItem(i);
                        if (pot != null && pot.getType() == Material.POTION) {
                            hasWater = true;
                        }
                    }

                    if (hasWater && stand.getFuelLevel() > 0) {
                        stand.setBrewingTime(0);
                        activeBrews.add(stand.getLocation());
                        startCustomBrewing(stand, inv, type);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private void startCustomBrewing(BrewingStand stand, BrewerInventory inv, String ingredientType) {
        Location loc = stand.getLocation();

        new BukkitRunnable() {
            int ticks = 5; // 5 секунд на варку напитка

            @Override
            public void run() {
                ItemStack currentIng = inv.getIngredient();
                if (currentIng == null || currentIng.getType() == Material.AIR || !currentIng.hasItemMeta()) {
                    this.cancel();
                    activeBrews.remove(loc);
                    return;
                }

                NamespacedKey key = new NamespacedKey(plugin, "cooking_ingredient");
                String type = currentIng.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (type == null || !type.equals(ingredientType)) {
                    this.cancel();
                    activeBrews.remove(loc);
                    return;
                }

                ticks--;
                loc.getWorld().playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 1.2f);

                if (ticks <= 0) {
                    this.cancel();
                    activeBrews.remove(loc);

                    ItemStack ing = inv.getIngredient();
                    if (ing != null) {
                        ing.setAmount(ing.getAmount() - 1);
                    }

                    stand.setFuelLevel(stand.getFuelLevel() - 1);
                    stand.update();

                    // Определяем, какое готовое блюдо выдать на основе ингредиента
                    String finalDishId = "mint_tea";
                    if (ingredientType.equals("rp_cucumber_blend")) finalDishId = "cucumber_lemonade";
                    if (ingredientType.equals("rp_pine_gather")) finalDishId = "pine_decoction";

                    ItemStack finalLiquid = plugin.getFoodManager().getDishById(finalDishId).createCookedItem(plugin);

                    for (int i = 0; i < 3; i++) {
                        ItemStack pot = inv.getItem(i);
                        if (pot != null && pot.getType() == Material.POTION) {
                            inv.setItem(i, finalLiquid.clone());
                        }
                    }
                    loc.getWorld().playSound(loc, Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}