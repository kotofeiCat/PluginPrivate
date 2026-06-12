package ru.politaboba.serverSystem.item.brewing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.HashMap;
import java.util.Map;

public class BrewingCauldronListener implements Listener {

    private final ServerSystem plugin;
    private final Map<Location, CauldronData> cookingCauldrons = new HashMap<>();

    public BrewingCauldronListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    // ИСПРАВЛЕНО: Аварийная очистка голограмм при перезагрузке/выключении сервера
    public void cleanup() {
        for (CauldronData data : cookingCauldrons.values()) {
            data.reset();
        }
        cookingCauldrons.clear();
    }

    @EventHandler
    public void onCauldronInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (event.getHand() != EquipmentSlot.HAND) return;

        Material blockType = block.getType();
        if (blockType != Material.CAULDRON && blockType != Material.WATER_CAULDRON) return;

        Block under = block.getRelative(0, -1, 0);
        if (under.getType() != Material.CAMPFIRE && under.getType() != Material.SOUL_CAMPFIRE) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (block.getBlockData() instanceof Levelled) {
            Levelled levelled = (Levelled) block.getBlockData();
            if (levelled.getLevel() == 0) {
                player.sendMessage("§cВ котле нет воды! Наполните его из ведра.");
                return;
            }
        } else if (blockType == Material.CAULDRON) {
            player.sendMessage("§cВ котле нет воды! Наполните его из ведра.");
            return;
        }

        CauldronData data = cookingCauldrons.computeIfAbsent(loc, k -> new CauldronData(loc));

        if (data.isCooking) {
            event.setCancelled(true);
            return;
        }

        if (hand.getType() == Material.WHEAT && !data.isReady) {
            event.setCancelled(true);
            if (data.wheatCount < 3) {
                data.wheatCount++;
                decreaseHandItem(player, hand);
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
                player.sendMessage("§e[Котел] Вы добавили Пшеницу (" + data.wheatCount + "/3)");
                checkRecipe(data);
            }
            return;
        }

        if (hand.getType() == Material.SUGAR && !data.isReady) {
            event.setCancelled(true);
            if (!data.hasSugar) {
                data.hasSugar = true;
                decreaseHandItem(player, hand);
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
                player.sendMessage("§e[Котел] Вы добавили Сахар (1/1)");
                checkRecipe(data);
            }
            return;
        }

        if (hand.getType() == Material.GLOWSTONE_DUST && !data.isReady) {
            event.setCancelled(true);
            if (!data.hasGlowstone) {
                data.hasGlowstone = true;
                decreaseHandItem(player, hand);
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
                player.sendMessage("§e[Котел] Вы засыпали Светящуюся пыль для активации брожения!");
                checkRecipe(data);
            }
            return;
        }

        if (hand.getType() == Material.GLASS_BOTTLE && data.isReady) {
            event.setCancelled(true);
            decreaseHandItem(player, hand);

            ItemStack beerWort = plugin.getFoodManager().getDishById("beer_wort").createCookedItem(plugin);
            if (!player.getInventory().addItem(beerWort).isEmpty()) {
                loc.getWorld().dropItemNaturally(loc, beerWort);
            }

            loc.getWorld().playSound(loc, Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
            player.sendMessage("§aВы успешно набрали Сырое Пивное Сусло в бутылочку!");

            data.reset();
            cookingCauldrons.remove(loc);
            block.setType(Material.CAULDRON);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        CauldronData data = cookingCauldrons.remove(loc);
        if (data != null) {
            data.reset();
        }
    }

    private void checkRecipe(CauldronData data) {
        if (data.wheatCount >= 3 && data.hasSugar && data.hasGlowstone && !data.isCooking) {
            data.isCooking = true;

            Location standLoc = data.loc.clone().add(0.5, 0.5, 0.5);
            ArmorStand stand = data.loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setCustomNameVisible(true);
                s.setCustomName("§6Варка сусла: 0%");
            });

            data.hologram = stand;

            new BukkitRunnable() {
                int progress = 0;
                @Override
                public void run() {
                    if (data.loc.getBlock().getType() != Material.WATER_CAULDRON || !stand.isValid()) {
                        this.cancel();
                        data.reset();
                        cookingCauldrons.remove(data.loc);
                        return;
                    }

                    progress += 20;
                    stand.setCustomName("§6Варка сусла: §e" + progress + "%");
                    data.loc.getWorld().playSound(data.loc, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 0.8f);

                    if (progress >= 100) {
                        this.cancel();
                        stand.setCustomName("§a§lСусло Готово! Используй бутылочку");
                        data.isReady = true;
                        data.isCooking = false;
                        data.loc.getWorld().playSound(data.loc, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.5f);
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    private void decreaseHandItem(Player player, ItemStack hand) {
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private static class CauldronData {
        Location loc;
        int wheatCount = 0;
        boolean hasSugar = false;
        boolean hasGlowstone = false;
        boolean isCooking = false;
        boolean isReady = false;
        ArmorStand hologram = null;

        CauldronData(Location loc) {
            this.loc = loc;
        }

        void reset() {
            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }
    }
}