package ru.politaboba.serverSystem.arrest;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.model.Faction;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ArrestListener implements Listener {

    private final ArrestManager arrestManager;
    private final ServerSystem plugin;

    public ArrestListener(ArrestManager arrestManager, ServerSystem plugin) {
        this.arrestManager = arrestManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Если выходит сам арестованный, снимаем с него статус во избежание вечного сохранения эффектов в файлах мира
        if (arrestManager.isArrested(playerUUID)) {
            arrestManager.releasePlayer(player);
        }

        // Если выходит коп, освобождаем тех, кого он вел
        arrestManager.releaseVictimsOfCop(playerUUID);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        if (arrestManager.isArrested(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();

        if (item.getType() == Material.IRON_BARS && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§c§lКандалы")) {
            event.setCancelled(true);

            UUID victimUUID = victim.getUniqueId();
            UUID attackerUUID = attacker.getUniqueId();

            if (arrestManager.isArrested(victimUUID)) {
                if (attackerUUID.equals(arrestManager.getCop(victimUUID))) {
                    arrestManager.releasePlayer(victim);
                    attacker.sendMessage("§aВы сняли кандалы с игрока " + victim.getName());
                }
                return;
            }

            boolean isWantedByBounty = plugin.getBountyOrders().containsKey(victimUUID);
            boolean isFactionEnemy = false;

            String attackerFactionName = plugin.getPlayerFactionMap().get(attackerUUID);
            if (attackerFactionName != null) {
                Faction attackerFaction = plugin.getFactions().get(attackerFactionName);
                if (attackerFaction != null && attackerFaction.getBlacklist() != null) {
                    if (attackerFaction.isBlacklisted(victimUUID)) {
                        isFactionEnemy = true;
                    }
                }
            }

            if (victim.getHealth() <= 8.0 || isWantedByBounty || isFactionEnemy) {
                arrestManager.arrestPlayer(victim, attacker);

                if (isWantedByBounty) {
                    attacker.sendMessage("§6[Охота] На игрока объявлен розыск! Вы мгновенно сковали его, несмотря на сопротивление.");
                } else if (isFactionEnemy) {
                    attacker.sendMessage("§c[Реестр] Этот преступник находится в ЧС твоего государства! Он арестован без права на сопротивление.");
                }
            } else {
                attacker.sendMessage("§cЖертва слишком сильно сопротивляется! Сначала ослабьте её (нужно меньше 4 сердец).");
                attacker.sendMessage("§7Примечание: Мгновенно арестовать можно только преступников из ЧС фракции или целей из /bounty.");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        Player cop = event.getPlayer();
        Player victim = (Player) event.getRightClicked();

        if (arrestManager.isArrested(victim.getUniqueId()) && cop.getInventory().getItemInMainHand().getType() == Material.LEAD) {
            event.setCancelled(true);
            cop.sendMessage("§aВы крепко держите повод кандалов игрока " + victim.getName());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (arrestManager.isArrested(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы закованны в кандалы и не можете ломать блоки!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (arrestManager.isArrested(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (arrestManager.isArrested(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете открывать сумки и инвентарь в кандалах!");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (arrestManager.isArrested(event.getPlayer().getUniqueId())) {
            String message = event.getMessage().toLowerCase();
            String cmd = message.split(" ")[0];

            // РЕШЕНИЕ БАГА: Блокируем только ТП-команды, сохраняя работоспособность /login, /register, /msg
            List<String> blockedCommands = Arrays.asList(
                    "/spawn", "/home", "/tp", "/warp", "/tpa", "/call", "/pay", "/rtp", "/sethome"
            );

            if (blockedCommands.contains(cmd)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cВ кандалах запрещено использовать телепортационные команды!");
            }
        }
    }
}