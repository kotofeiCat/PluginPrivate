package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class DungeonListener implements Listener {

    private final DungeonManager dungeonManager;
    private final NamespacedKey castleKey;
    private final NamespacedKey waveMobKey;

    public DungeonListener(JavaPlugin plugin, DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
        this.castleKey = new NamespacedKey(plugin, "castle_key");
        this.waveMobKey = new NamespacedKey(plugin, "dungeon_wave_mob");
    }

    // 1. Старт данжа или вход в лобби по нажатию на кнопку
    @EventHandler
    public void onLobbyClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();

            // Если игрок кликнул ПКМ по кнопке в лобби
            if (block != null && block.getType().name().contains("BUTTON")) {
                DungeonSession session = dungeonManager.getNearestSession(block.getLocation());

                if (session != null && session.getState() == DungeonSession.State.LOBBY) {
                    // ИСПРАВЛЕНО: Вместо удаленного старого метода вызываем готовность кликнувшего игрока
                    session.handlePlayerReady(event.getPlayer());
                }
            }
        }
    }

    // 2. Открытие дверей ключом
    @EventHandler
    public void onDoorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(castleKey, PersistentDataType.BOOLEAN)) {
            DungeonSession session = dungeonManager.getNearestSession(block.getLocation());
            if (session != null) {
                if (session.tryOpenDoor(block)) {
                    event.setCancelled(true);
                    item.setAmount(item.getAmount() - 1); // Забираем ключ
                }
            }
        }
    }

    // 3. Отслеживание смертей мобов волны
    @EventHandler
    public void onWaveMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(waveMobKey, PersistentDataType.BOOLEAN)) {
            DungeonSession session = dungeonManager.getNearestSession(entity.getLocation());
            if (session != null) {
                session.handleMobDeath(entity);
            }
        }
    }

    // 4. Отслеживание смертей игроков для проигрыша всей группы
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonSession session = dungeonManager.getNearestSession(player.getLocation());
        if (session != null) {
            session.handlePlayerDeath(player);
        }
    }

    // 5. Отслеживание смерти босса данжа для триггера победы
    @EventHandler
    public void onBossEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(ArchVindicatorBoss.BOSS_KEY, PersistentDataType.BOOLEAN)) {
            DungeonSession session = dungeonManager.getNearestSession(entity.getLocation());
            if (session != null) {
                session.handleBossCompleted(entity.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonSession session = dungeonManager.getNearestSession(player.getLocation());
        if (session != null) {
            session.handlePlayerQuit(player);
        }
    }
}