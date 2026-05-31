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
import org.bukkit.inventory.ItemStack;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.model.Faction;

import java.util.UUID;

public class ArrestListener implements Listener {

    private final ArrestManager arrestManager;
    private final ServerSystem plugin; // Добавляем ссылку на плагин для проверки ЧС и Охоты

    public ArrestListener(ArrestManager arrestManager, ServerSystem plugin) {
        this.arrestManager = arrestManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        // Запрещаем закованному игроку атаковать кого-либо
        if (arrestManager.isArrested(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = attacker.getInventory().getItemInMainHand();

        // Проверяем удар кандалами
        if (item.getType() == Material.IRON_BARS && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§c§lКандалы")) {
            event.setCancelled(true); // Отменяем ванильный урон

            UUID victimUUID = victim.getUniqueId();
            UUID attackerUUID = attacker.getUniqueId();

            // Если игрок уже арестован этим копом — освобождаем его
            if (arrestManager.isArrested(victimUUID)) {
                if (attackerUUID.equals(arrestManager.getCop(victimUUID))) {
                    arrestManager.releasePlayer(victim);
                    attacker.sendMessage("§aВы сняли кандалы с игрока " + victim.getName());
                }
                return;
            }

            // --- ИНТЕГРАЦИЯ С ОХОТОЙ ЗА ГОЛОВАМИ И ЧС ФРАКЦИЙ ---
            boolean isWantedByBounty = plugin.getBountyOrders().containsKey(victimUUID);
            boolean isFactionEnemy = false;

            // Проверяем, является ли жертва врагом фракции нападающего
            String attackerFactionName = plugin.getPlayerFactionMap().get(attackerUUID);
            if (attackerFactionName != null) {
                Faction attackerFaction = plugin.getFactions().get(attackerFactionName);
                if (attackerFaction != null && attackerFaction.getBlacklist() != null) {
                    if (attackerFaction.isBlacklisted(victimUUID)) {
                        isFactionEnemy = true;
                    }
                }
            }

            // Условие ареста: Сниженное HP ИЛИ на игрока объявлена охота ИЛИ игрок в ЧС нашей фракции
            if (victim.getHealth() <= 8.0 || isWantedByBounty || isFactionEnemy) {

                arrestManager.arrestPlayer(victim, attacker);

                // Выводим РП-сообщение о причине мгновенного ареста без сопротивления
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

    // Запрещаем закованному взаимодействовать (вести на поводке при клике ПКМ)
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

    // ОГРАНИЧЕНИЯ ДЛЯ АРЕСТОВАННОГО
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
            String cmd = event.getMessage().toLowerCase();
            if (!cmd.startsWith("/c ") && !cmd.startsWith("/contract")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cВ кандалах запрещено использовать телепортационные команды!");
            }
        }
    }
}