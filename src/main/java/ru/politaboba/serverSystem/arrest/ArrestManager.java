package ru.politaboba.serverSystem.arrest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.*;

public class ArrestManager {

    private final ServerSystem plugin;
    private final Map<UUID, UUID> arrestedPlayers = new HashMap<>();

    public ArrestManager(ServerSystem plugin) {
        this.plugin = plugin;
        startDragTask();
    }

    public void arrestPlayer(Player victim, Player cop) {
        arrestedPlayers.put(victim.getUniqueId(), cop.getUniqueId());

        victim.sendMessage("§c§l[АРЕСТ] §cВы были закованы в кандалы игроком §e" + cop.getName() + "§c!");
        cop.sendMessage("§a§l[АРЕСТ] §aВы успешно заковали в кандалы §e" + victim.getName() + "§a. Ведите его за собой!");

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 4, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
    }

    public void releasePlayer(Player victim) {
        arrestedPlayers.remove(victim.getUniqueId());
        victim.removePotionEffect(PotionEffectType.SLOWNESS);
        victim.removePotionEffect(PotionEffectType.BLINDNESS);
        victim.sendMessage("§a§l[АРЕСТ] §aС вас сняли кандалы! Вы свободны.");
    }

    // РЕШЕНИЕ БАГА: Освобождение арестованных, если конвоир вышел из игры
    public void releaseVictimsOfCop(UUID copUUID) {
        List<UUID> toRelease = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : arrestedPlayers.entrySet()) {
            if (entry.getValue().equals(copUUID)) {
                toRelease.add(entry.getKey());
            }
        }
        for (UUID victimUUID : toRelease) {
            Player victim = Bukkit.getPlayer(victimUUID);
            if (victim != null && victim.isOnline()) {
                releasePlayer(victim);
            } else {
                arrestedPlayers.remove(victimUUID);
            }
        }
    }

    public boolean isArrested(UUID uuid) {
        return arrestedPlayers.containsKey(uuid);
    }

    public UUID getCop(UUID victimUUID) {
        return arrestedPlayers.get(victimUUID);
    }

    public static ItemStack createHandcuffs() {
        ItemStack cuffs = new ItemStack(Material.IRON_BARS);
        ItemMeta meta = cuffs.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lКандалы");
            List<String> lore = new ArrayList<>();
            lore.add("§7Ударьте игрока, у которого");
            lore.add("§7осталось меньше §c4 сердец§7,");
            lore.add("§7чтобы ограничить его свободу.");
            meta.setLore(lore);
            cuffs.setItemMeta(meta);
        }
        return cuffs;
    }

    private void startDragTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, UUID> entry : arrestedPlayers.entrySet()) {
                Player victim = Bukkit.getPlayer(entry.getKey());
                Player cop = Bukkit.getPlayer(entry.getValue());

                if (victim != null && victim.isOnline() && cop != null && cop.isOnline()) {
                    Location copLoc = cop.getLocation();
                    Location vicLoc = victim.getLocation();

                    if (copLoc.getWorld().equals(vicLoc.getWorld())) {
                        double distance = copLoc.distance(vicLoc);

                        if (distance > 15) {
                            victim.teleport(copLoc);
                        } else if (distance > 3) {
                            Vector direction = copLoc.toVector().subtract(vicLoc.toVector()).normalize();
                            victim.setVelocity(direction.multiply(0.4));
                        }
                    }
                }
            }
        }, 0L, 2L);
    }
}