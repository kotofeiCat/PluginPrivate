package ru.politaboba.serverSystem.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.Collections;
import java.util.UUID;

public class BountyListener implements Listener {

    private final ServerSystem plugin;

    public BountyListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Защита инвентаря на основе кастомного Holder
        if (!(event.getInventory().getHolder() instanceof BountyCommand.BountyBoardHolder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.OAK_SIGN) {
            player.closeInventory();

            player.sendMessage(" ");
            player.sendMessage("§c§l[Охота за Головами] §7Чтобы быстро оформить заказ:");

            net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent("§6§l👉 НАЖМИ СЮДА, ЧТОБЫ НАЧАТЬ ВВОД 👈");

            message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                    "/bounty target "
            ));

            message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§aКликни, и команда сама появится в твоем чате!").create()
            ));

            player.spigot().sendMessage(message);
            player.sendMessage(" ");
            return;
        }

        if (clickedItem.getType() == Material.CHEST && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName().equals("§aСдать контракт")) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand.getType() != Material.PLAYER_HEAD) {
                player.sendMessage("§cВы должны держать в главной руке голову жертвы!");
                return;
            }

            SkullMeta headMeta = (SkullMeta) itemInHand.getItemMeta();
            if (headMeta == null || headMeta.getOwningPlayer() == null) return;

            UUID victimUUID = headMeta.getOwningPlayer().getUniqueId();

            if (plugin.getBountyOrders().containsKey(victimUUID)) {
                BountyOrder order = plugin.getBountyOrders().remove(victimUUID);

                // РЕШЕНИЕ БАГА: Выносим дисковые операции ввода-вывода (I/O) в асинхронный поток для сохранения стабильного TPS
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getFactionDataManager().saveAll();
                });

                itemInHand.setAmount(itemInHand.getAmount() - 1);

                ItemStack diamonds = new ItemStack(Material.DIAMOND, order.getRewardAmount());
                player.getInventory().addItem(diamonds);

                player.sendMessage("§a§lКонтракт выполнен! §eВы успешно сдали голову §c" + order.getTargetName() + " §aи получили §b" + order.getRewardAmount() + " алмазов§a!");
                player.closeInventory();
            } else {
                player.sendMessage("§cЗа голову этого игрока сейчас нет активной награды.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUUID = victim.getUniqueId();

        if (plugin.getBountyOrders().containsKey(victimUUID)) {
            BountyOrder order = plugin.getBountyOrders().get(victimUUID);

            if (event.getKeepInventory()) {
                event.setKeepInventory(false);
                event.getDrops().clear();

                for (ItemStack item : victim.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        event.getDrops().add(item);
                    }
                }

                event.setKeepLevel(false);
                event.setDroppedExp(Math.min(victim.getTotalExperience(), 100));
            }

            ItemStack victimHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) victimHead.getItemMeta();

            Player killer = victim.getKiller();
            String killerName = (killer != null) ? killer.getName() : "Окружающая среда / Ловушка";

            if (headMeta != null) {
                headMeta.setOwningPlayer(victim);
                headMeta.setDisplayName("§cГолова: " + victim.getName());
                headMeta.setLore(Collections.singletonList("§7Заказной контракт выполнен: §e" + killerName));
                victimHead.setItemMeta(headMeta);
            }

            event.getDrops().add(victimHead);

            if (killer != null) {
                killer.sendMessage("§c§l[Охота за Головами] §aВы ликвидировали цель! Заберите её вещи и голову для сдачи в §6/bounty§a.");
                killer.playSound(killer.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f);
            }

            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§4§l☠ [ЛИКВИДАЦИЯ] ☠");
            Bukkit.broadcastMessage("§cЗаказ на голову §e" + victim.getName() + " §cбыл успешно исполнен!");
            Bukkit.broadcastMessage("§7Исполнитель: §f" + killerName + " §e| §7Сумма контракта: §b" + order.getRewardAmount() + " 💎");
            Bukkit.broadcastMessage("§4⚠️ Весь инвентарь жертвы был утерян из-за криминального статуса!");
            Bukkit.broadcastMessage(" ");
        }
    }
}