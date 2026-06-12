package ru.politaboba.serverSystem.cases;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.politaboba.serverSystem.cases.CaseCommand.CaseGuiHolder;

public class CaseListener implements Listener {

    private final CaseManager caseManager;

    public CaseListener(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaseGuiHolder)) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String caseId = null;
        if (clicked.getType() == Material.CHEST) {
            caseId = "common_case";
        } else if (clicked.getType() == Material.TRAPPED_CHEST) {
            caseId = "rare_case";
        } else if (clicked.getType() == Material.ENDER_CHEST) {
            caseId = "epic_case";
        } else if (clicked.getType() == Material.BEACON) {
            caseId = "legendary_case";
        }

        if (caseId != null) {
            int cost = caseManager.getCaseCost(caseId);

            if (caseManager.removeCoins(player.getUniqueId(), cost)) {
                player.closeInventory();

                ItemStack reward = caseManager.rollLoot(caseId);

                if (!player.getInventory().addItem(reward).isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), reward);
                }

                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 35, 0.3, 0.3, 0.3, 0.2);

                String itemName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                        ? reward.getItemMeta().getDisplayName()
                        : reward.getType().name();

                player.sendMessage("§a§l[Кейсы] §fВы успешно открыли кейс и получили: " + itemName);

                // Глобальное оповещение для Эпического и Легендарного кейсов
                if (caseId.equals("epic_case") || caseId.equals("legendary_case")) {
                    String caseName = clicked.getItemMeta().getDisplayName();
                    Bukkit.broadcastMessage("§6§l🎁 [Удача] §e" + player.getName() + " §fоткрыл " + caseName + " §fи достал: " + itemName);
                }
            } else {
                player.sendMessage("§c§l[Ошибка] §cНедостаточно Поликоинов для покупки этого кейса!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }
}