package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onLobbyClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Оптимизация: Быстрая проверка на тип блока во избежание лишних вычислений локаций
        String blockTypeName = block.getType().name();
        if (blockTypeName.contains("BUTTON") || blockTypeName.contains("LEVER")) {
            DungeonSession session = dungeonManager.getNearestSession(block.getLocation());
            if (session != null && session.getState() == DungeonSession.State.LOBBY) {
                session.handlePlayerReady(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDoorInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(castleKey, PersistentDataType.BOOLEAN)) {
            // Отменяем сразу, чтобы трипвайр-хук не ставился как блок на стену
            event.setCancelled(true);

            DungeonSession session = dungeonManager.getNearestSession(block.getLocation());
            if (session != null) {
                if (session.tryOpenDoor(block)) {
                    // ИСПРАВЛЕНО: Безопасное уменьшение предмета в текущей руке (работает и для MainHand, и для OffHand)
                    item.subtract(1);
                }
            }
        }
    }

    @EventHandler
    public void onWaveMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // Сначала проверяем PDC (работает мгновенно), чтобы не нагружать сервер поиском сессий при смерти каждой свиньи
        if (entity.getPersistentDataContainer().has(waveMobKey, PersistentDataType.BOOLEAN)) {
            DungeonSession session = dungeonManager.getNearestSession(entity.getLocation());
            if (session != null) {
                session.handleMobDeath(entity);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Ищем сессию по локации смерти игрока
        DungeonSession session = dungeonManager.getNearestSession(player.getLocation());
        if (session != null) {
            session.handlePlayerDeath(player);
        }
    }

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Вместо непредсказуемой локации выхода ищем игрока перебором сессий (так как он 100% записан в List<UUID> players)
        for (DungeonSession session : dungeonManager.getActiveSessions()) {
            try {
                java.lang.reflect.Field field = session.getClass().getDeclaredField("players");
                field.setAccessible(true);
                java.util.List<?> playersList = (java.util.List<?>) field.get(session);

                if (playersList != null && playersList.contains(player.getUniqueId())) {
                    session.handlePlayerQuit(player);
                    break;
                }
            } catch (Exception e) {
                // Если рефлексия не сработала — фолбэк на старый метод по локации
                DungeonSession fallbackSession = dungeonManager.getNearestSession(player.getLocation());
                if (fallbackSession != null) {
                    fallbackSession.handlePlayerQuit(player);
                }
                break;
            }
        }
    }

    @EventHandler
    public void onAnvilPrepare(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        org.bukkit.inventory.AnvilInventory anvil = event.getInventory();
        ItemStack first = anvil.getItem(0);  // Оружие или броня
        ItemStack second = anvil.getItem(1); // Наша кастомная книга

        // Проверяем, что в наковальню положили и вещь, и книгу
        if (first == null || second == null || second.getType() != org.bukkit.Material.ENCHANTED_BOOK) return;

        org.bukkit.inventory.meta.EnchantmentStorageMeta bookMeta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) second.getItemMeta();
        if (bookMeta == null || !bookMeta.hasStoredEnchants()) return;

        // Клонируем первый предмет, чтобы создать результат без порчи оригинала
        ItemStack result = first.clone();
        org.bukkit.inventory.meta.ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        boolean upgraded = false;
        int totalLevelCost = 0;

        // Перебираем все чары, зашитые в книгу (используем полный путь к Map.Entry, чтобы не зависеть от импортов)
        for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
            org.bukkit.enchantments.Enchantment enchant = entry.getKey();
            int bookLevel = entry.getValue();

            // СОВРЕМЕННАЯ ПРОВЕРКА: Подходит ли книга к предмету в новых версиях 1.20.5+ / 1.21+
            // Метод canEnchantItem() теперь сам внутри проверяет теги реестра (заменяет getItemTarget)
            if (!enchant.canEnchantItem(result) && result.getType() != org.bukkit.Material.ENCHANTED_BOOK) {
                // Если мы скрещиваем книгу не с другой книгой, и чара физически не лезет на этот предмет (например, Острота на Шлем) -> пропускаем
                continue;
            }

            int currentLevel = resultMeta.getEnchantLevel(enchant);
            int finalLevel = currentLevel;

            if (bookLevel > currentLevel) {
                // Если в книге уровень выше (на мече V, в книге VI) -> ставим уровень книги (VI)
                finalLevel = bookLevel;
            } else if (bookLevel == currentLevel) {
                // Если уровни равны (VI и VI) -> повышаем уровень на один (VII)
                finalLevel = currentLevel + 1;
            } else {
                // Если в книге уровень меньше, чем на мече -> оставляем как есть
                continue;
            }

            // Ограничиваем максимальный уровень Остроты/Защиты/Силы до VII
            if (finalLevel > 7) finalLevel = 7;

            if (finalLevel > currentLevel) {
                // true — принудительно игнорирует ванильные лимиты уровней
                resultMeta.addEnchant(enchant, finalLevel, true);
                upgraded = true;
                totalLevelCost += finalLevel * 3;
            }
        }

        // Если предмет обновился, выкатываем его в слот результата наковальни
        if (upgraded) {
            result.setItemMeta(resultMeta);
            event.setResult(result);

            // Магия Paper/Spigot: принудительно ставим стоимость ремонта
            // Так как уровни высокие, ванилла может написать "Слишком дорого!", мы ставим адекватную цену в XP
            int finalCost = Math.max(5, totalLevelCost);

            org.bukkit.Bukkit.getScheduler().runTask(org.bukkit.Bukkit.getPluginManager().getPlugin("serverSystem"), () -> {
                if (anvil.getViewers().isEmpty()) return;

                org.bukkit.entity.HumanEntity viewer = anvil.getViewers().get(0);

                // Сразу получаем AnvilView, если игрок смотрит в наковальню
                if (viewer.getOpenInventory() instanceof org.bukkit.inventory.view.AnvilView anvilView) {
                    anvilView.setRepairCost(finalCost);
                }
            });
        }
    }
}