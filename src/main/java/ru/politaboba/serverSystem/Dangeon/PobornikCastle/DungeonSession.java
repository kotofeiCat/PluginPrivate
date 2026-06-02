package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DungeonSession {

    public enum State { LOBBY, WAVES, LOOTING, BOSS, COMPLETED }

    private final JavaPlugin plugin;
    private final Location origin;
    private final ArchVindicatorBoss bossMechanics;
    private final DungeonManager dungeonManager; // Ссылка на менеджер для регенерации блоков

    private State state = State.LOBBY;
    private final List<UUID> players = new ArrayList<>();
    private final List<Entity> waveMonsters = new ArrayList<>();
    private int currentWave = 1;

    private UUID roomCreator = null;
    private UUID activeBossUUID = null; // Храним UUID текущего заспавненного босса
    private final int MAX_PLAYERS = 5;
    private final BossBar lobbyBossBar;

    private final NamespacedKey waveMobKey;
    private final NamespacedKey castleKey;

    public DungeonSession(JavaPlugin plugin, Location origin, ArchVindicatorBoss bossMechanics, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.origin = origin;
        this.bossMechanics = bossMechanics;
        this.dungeonManager = dungeonManager;
        this.waveMobKey = new NamespacedKey(plugin, "dungeon_wave_mob");
        this.castleKey = new NamespacedKey(plugin, "castle_key");

        this.lobbyBossBar = BossBar.bossBar(
                Component.text("Ожидание игроков... (0/" + MAX_PLAYERS + ")", NamedTextColor.YELLOW),
                0.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
    }

    public void handlePlayerReady(Player player) {
        // ЗАЩИТА ОТ ПОСТОРОННИХ: Если стейт не LOBBY, значит кто-то уже проходит данж прямо сейчас!
        if (state != State.LOBBY) {
            player.sendMessage(Component.text("Внутри данжа уже находится другая группа! Дождитесь окончания их зачистки.", NamedTextColor.RED));
            return;
        }

        if (players.size() >= MAX_PLAYERS) {
            player.sendMessage(Component.text("Лобби данжа уже заполнено!", NamedTextColor.RED));
            return;
        }

        if (players.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("Вы уже подтвердили готовность!", NamedTextColor.GREEN));
            return;
        }

        Location min = getOffsetLocation("dungeon.lobby-zone.min");
        Location max = getOffsetLocation("dungeon.lobby-zone.max");

        if (!isInZone(player.getLocation(), min, max)) {
            player.sendMessage(Component.text("Вы должны находиться внутри стартовой зоны лобби данжа!", NamedTextColor.RED));
            return;
        }

        if (players.isEmpty()) {
            roomCreator = player.getUniqueId();
            player.sendMessage(Component.text("Вы стали создателем комнаты! Введите /dungeon start для запуска.", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        }

        players.add(player.getUniqueId());
        player.showBossBar(this.lobbyBossBar);
        updateLobbyBossBar();

        broadcast(Component.text("Игрок " + player.getName() + " готов к бою! (" + players.size() + "/" + MAX_PLAYERS + ")", NamedTextColor.GREEN));
    }

    private void updateLobbyBossBar() {
        float progress = (float) players.size() / (float) MAX_PLAYERS;
        this.lobbyBossBar.progress(progress);
        String creatorName = Bukkit.getOfflinePlayer(roomCreator).getName();
        this.lobbyBossBar.name(Component.text("Группа данжа: " + players.size() + "/" + MAX_PLAYERS + " | Лидер: " + creatorName, NamedTextColor.GOLD));
    }

    public void handleForceStart(Player player) {
        if (state != State.LOBBY) return;

        if (roomCreator == null || !roomCreator.equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Только создатель комнаты может запустить данж!", NamedTextColor.RED));
            return;
        }

        startFirstStage();
    }

    private void startFirstStage() {
        this.state = State.WAVES;
        // Добавляем +1.0 к Y, чтобы игроки телепортировались НА блок, а не по пояс в него
        Location spawnLoc = getOffsetLocation("dungeon.stage1-spawn").add(0.5, 1.0, 0.5);

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(this.lobbyBossBar);
        }

        broadcast(Component.text("Лидер запустил игру! Защитите комнату от волн поборников!", NamedTextColor.RED).decorate(TextDecoration.BOLD));

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.teleport(spawnLoc);
        }

        spawnWave();
    }

    public void spawnWave() {
        if (currentWave > 3) {
            endWaveStage();
            return;
        }

        List<Map<?, ?>> spawnOffsets = plugin.getConfig().getMapList("dungeon.wave-spawns");
        if (spawnOffsets.isEmpty()) {
            plugin.getLogger().severe("[Dungeon] Не настроены точки спавна мобов wave-spawns!");
            endWaveStage();
            return;
        }

        org.bukkit.configuration.ConfigurationSection waveSection =
                plugin.getConfig().getConfigurationSection("dungeon.wave-settings.wave" + currentWave);

        if (waveSection == null) {
            endWaveStage();
            return;
        }

        Random random = new Random();
        broadcast(Component.text("⚠️ ВОЛНА " + currentWave + " НАЧАЛАСЬ! Защищайтесь!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        for (String key : waveSection.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                int count = waveSection.getInt(key);

                for (int i = 0; i < count; i++) {
                    Map<?, ?> randomOffset = spawnOffsets.get(random.nextInt(spawnOffsets.size()));

                    // ИСПРАВЛЕНО: убрали двойной getSafeInt и добавили безопасное смещение .add(0.5, 1.0, 0.5)
                    // Благодаря этому моб заспавнится ровно по центру блока и НАД его поверхностью
                    Location loc = origin.clone().add(
                            getSafeInt(randomOffset.get("x")),
                            getSafeInt(randomOffset.get("y")),
                            getSafeInt(randomOffset.get("z"))
                    ).add(0.5, 1.0, 0.5);

                    // Используем нативный класс моба для проверки спавна
                    Entity spawned = loc.getWorld().spawnEntity(loc, type);
                    if (spawned instanceof org.bukkit.entity.Monster monster) {
                        setupCustomMob(monster);
                        waveMonsters.add(monster);
                    } else if (spawned != null) {
                        // Если заспавнился не Monster (например, ведьма или кастомная сущность),
                        // все равно добавляем в список, чтобы волна не скипалась
                        spawned.getPersistentDataContainer().set(waveMobKey, PersistentDataType.BOOLEAN, true);
                        waveMonsters.add(spawned);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("[Dungeon] Ошибка в конфиге! Неизвестный тип моба: " + key);
            }
        }

        if (waveMonsters.isEmpty()) {
            plugin.getLogger().warning("[Dungeon] Волна " + currentWave + " пуста или мобы не смогли заспавниться. Переход к луту.");
            endWaveStage();
        }
    }

    // Метод кастомизации характеристик мобов
    private void setupCustomMob(org.bukkit.entity.Monster monster) {
        // Обязательно вешаем нашу PDC метку, чтобы DungeonListener её засчитывал при смерти
        monster.getPersistentDataContainer().set(waveMobKey, PersistentDataType.BOOLEAN, true);
        monster.setRemoveWhenFarAway(false);
        monster.setCustomNameVisible(true);

        String typeName = monster.getType().name();
        org.bukkit.inventory.EntityEquipment equip = monster.getEquipment();

        // Распределяем РП-характеристики в зависимости от типа заспавненного моба
        switch (typeName) {
            case "ZOMBIE":
                monster.customName(Component.text("Чумной Воитель [Волна " + currentWave + "]", NamedTextColor.RED));
                setMobMaxHealth(monster, 40.0); // 40 HP (в 2 раза больше ванильного)
                setMobSpeed(monster, 0.28);     // Слегка ускоряем
                if (equip != null) {
                    equip.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                    equip.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
                break;

            case "SKELETON":
                monster.customName(Component.text("Замковый Лучник [Волна " + currentWave + "]", NamedTextColor.GRAY));
                setMobMaxHealth(monster, 25.0);
                if (equip != null) {
                    equip.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                }
                break;

            case "VINDICATOR":
                monster.customName(Component.text("Элитный Палач [Волна " + currentWave + "]", NamedTextColor.GOLD));
                setMobMaxHealth(monster, 60.0); // Жирный штурмовик
                setMobSpeed(monster, 0.32);     // Очень быстрый бег
                if (equip != null) {
                    equip.setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
                }
                break;

            case "WITHER_SKELETON":
                monster.customName(Component.text("Гвардеец Преисподней [Волна " + currentWave + "]", NamedTextColor.DARK_RED));
                setMobMaxHealth(monster, 50.0);
                if (equip != null) {
                    equip.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    equip.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                }
                break;

            default:
                monster.customName(Component.text("Рейдер Замка [Волна " + currentWave + "]", NamedTextColor.RED));
                break;
        }

        // НАПАДЕНИЕ (АГР): Насильно заставляем моба бежать и атаковать ближайшего участника данжа
        Player nearestTarget = findNearestDungeonPlayer(monster.getLocation());
        if (nearestTarget != null) {
            monster.setTarget(nearestTarget);
        }
    }

    // Утилита изменения здоровья
    private void setMobMaxHealth(org.bukkit.entity.Monster monster, double health) {
        org.bukkit.attribute.AttributeInstance attr = monster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(health);
            monster.setHealth(health); // Восстанавливаем здоровье до нового максимума
        }
    }

    // Утилита изменения скорости перемещения
    private void setMobSpeed(org.bukkit.entity.Monster monster, double speed) {
        org.bukkit.attribute.AttributeInstance attr = monster.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(speed);
        }
    }

    // Поиск ближайшего живого игрока, участвующего в прохождении данжа
    private Player findNearestDungeonPlayer(Location loc) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.isDead()) {
                double distSquared = p.getLocation().distanceSquared(loc);
                if (distSquared < nearestDist) {
                    nearestDist = distSquared;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    public void handleMobDeath(Entity entity) {
        if (state != State.WAVES) return;
        waveMonsters.remove(entity);

        if (waveMonsters.isEmpty()) {
            currentWave++;
            if (currentWave <= 3) {
                broadcast(Component.text("Волна зачищена! Приготовьтесь к следующей...", NamedTextColor.GOLD));
                Bukkit.getScheduler().runTaskLater(plugin, this::spawnWave, 100L);
            } else {
                endWaveStage();
            }
        }
    }

    private void endWaveStage() {
        this.state = State.LOOTING;
        broadcast(Component.text("Вы выстояли! Вам выдан Ключ. Откройте дверь во 2-ю секцию!", NamedTextColor.GREEN));

        Player lucky = null;
        for (UUID uuid : players) {
            lucky = Bukkit.getPlayer(uuid);
            if (lucky != null) break;
        }

        if (lucky != null) {
            lucky.getInventory().addItem(createDungeonKey());
        }

        generateLootTables();
    }

    public boolean tryOpenDoor(Block block) {
        if (state != State.LOOTING) return false;

        // ИСПРАВЛЕНО: Проверяем, является ли кликнутый блок ВЕРХНЕЙ половинкой двери.
        // Если да — переключаемся на нижний блок под ним, чтобы координаты сошлись с конфигом!
        if (block.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
            if (bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
                block = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            }
        }

        List<Map<?, ?>> doorOffsets = plugin.getConfig().getMapList("dungeon.doors-stage1");
        boolean isDoorBlock = false;

        for (Map<?, ?> offset : doorOffsets) {
            Location loc = origin.clone().add(getSafeInt(offset.get("x")), getSafeInt(offset.get("y")), getSafeInt(offset.get("z")));
            if (loc.getBlockX() == block.getX() && loc.getBlockY() == block.getY() && loc.getBlockZ() == block.getZ()) {
                isDoorBlock = true;
                break;
            }
        }

        if (isDoorBlock) {
            for (Map<?, ?> offset : doorOffsets) {
                Location loc = origin.clone().add(getSafeInt(offset.get("x")), getSafeInt(offset.get("y")), getSafeInt(offset.get("z")));
                Block doorPart = loc.getBlock();

                if (doorPart.getType() == Material.IRON_DOOR) {
                    doorPart.setType(Material.AIR, false);
                    Block upperPart = doorPart.getRelative(0, 1, 0);
                    if (upperPart.getType() == Material.IRON_DOOR) {
                        upperPart.setType(Material.AIR, false);
                    }
                } else {
                    doorPart.setType(Material.AIR, false);
                }
            }

            block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.5f);
            this.state = State.BOSS;
            spawnBossStage();
            return true;
        }
        return false;
    }

    private void generateLootTables() {
        List<Map<?, ?>> chestOffsets = plugin.getConfig().getMapList("dungeon.loot-chests");
        Random random = new Random();

        // Загружаем зачарования по современному стандарту Paper 1.21 через Registry
        org.bukkit.enchantments.Enchantment sharpness = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
        org.bukkit.enchantments.Enchantment protection = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
        org.bukkit.enchantments.Enchantment power = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));

        for (Map<?, ?> offset : chestOffsets) {
            Location loc = origin.clone().add(getSafeInt(offset.get("x")), getSafeInt(offset.get("y")), getSafeInt(offset.get("z")));
            Block block = loc.getBlock();

            // Интерфейс Container объединяет под собой и Chest, и Barrel
            if (block.getState() instanceof org.bukkit.block.Container container) {
                org.bukkit.inventory.Inventory inv = container.getInventory();
                inv.clear(); // Очищаем хранилище перед заполнением

                // Определяем, сколько слотов в контейнере будет заполнено вещами (от 4 до 8 слотов)
                int itemsCount = 4 + random.nextInt(5);

                for (int i = 0; i < itemsCount; i++) {
                    int randomSlot = random.nextInt(inv.getSize());

                    // Чтобы вещи не перезаписывали друг друга в одном слоте, ищем пустой
                    if (inv.getItem(randomSlot) != null) continue;

                    double roll = random.nextDouble(); // Ролл от 0.0 до 1.0

                    // 1. ШАНС 7% — Кастомные сверхмощные книги зачарований
                    if (roll < 0.07) {
                        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                        org.bukkit.inventory.meta.EnchantmentStorageMeta meta = (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();

                        if (meta != null) {
                            int bookType = random.nextInt(3); // 0 = Защита, 1 = Острота, 2 = Сила

                            if (bookType == 0 && protection != null) {
                                int[] levels = {6, 7, 8};
                                int lvl = levels[random.nextInt(levels.length)];
                                meta.addStoredEnchant(protection, lvl, true); // true убирает лимит уровня!

                                // ИСПРАВЛЕНО: применяем displayName к объекту meta
                                meta.displayName(net.kyori.adventure.text.Component.text("Забытый фолиант: Защита " + lvl, net.kyori.adventure.text.format.NamedTextColor.AQUA));
                            }
                            else if (bookType == 1 && sharpness != null) {
                                int[] levels = {6, 7, 8, 9};
                                int lvl = levels[random.nextInt(levels.length)];
                                meta.addStoredEnchant(sharpness, lvl, true);

                                // ИСПРАВЛЕНО: применяем displayName к объекту meta
                                meta.displayName(net.kyori.adventure.text.Component.text("Древний манускрипт: Острота " + lvl, net.kyori.adventure.text.format.NamedTextColor.RED));
                            }
                            else if (bookType == 2 && power != null) {
                                int[] levels = {7, 9};
                                int lvl = levels[random.nextInt(levels.length)];
                                meta.addStoredEnchant(power, lvl, true);

                                // ИСПРАВЛЕНО: применяем displayName к объекту meta
                                meta.displayName(net.kyori.adventure.text.Component.text("Эльфийские писания: Сила " + lvl, net.kyori.adventure.text.format.NamedTextColor.GREEN));
                            }

                            // В самом конце мета применится к книге: book.setItemMeta(meta);
                            book.setItemMeta(meta);
                            inv.setItem(randomSlot, book);
                        }
                    }
                    // 2. ШАНС 8% — Незеритовые слитки (Иногда)
                    else if (roll < 0.15) {
                        inv.setItem(randomSlot, new ItemStack(Material.NETHERITE_INGOT, 1));
                    }
                    // 3. ШАНС 20% — Алмазное кастомное оружие
                    else if (roll < 0.35) {
                        Material[] weapons = {Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
                        Material chosenWeapon = weapons[random.nextInt(weapons.length)];
                        inv.setItem(randomSlot, new ItemStack(chosenWeapon, 1));
                    }
                    // 4. ШАНС 25% — Чистые алмазы или изумруды
                    else if (roll < 0.60) {
                        if (random.nextBoolean()) {
                            inv.setItem(randomSlot, new ItemStack(Material.DIAMOND, 1 + random.nextInt(3))); // 1-3 шт
                        } else {
                            inv.setItem(randomSlot, new ItemStack(Material.EMERALD, 2 + random.nextInt(5))); // 2-6 шт
                        }
                    }
                    // 5. ВСЕ ОСТАЛЬНОЕ (40%) — Железо
                    else {
                        inv.setItem(randomSlot, new ItemStack(Material.IRON_INGOT, 3 + random.nextInt(6))); // 3-8 шт
                    }
                }
                // Сохраняем изменения в блоке контейнера
                container.update();
            }
        }
    }

    private void spawnBossStage() {
        Location bossLoc = getOffsetLocation("dungeon.boss-spawn");
        // Запоминаем UUID босса, чтобы понять, когда именно НАШ босс умрет
        Evoker boss = bossMechanics.spawn(bossLoc);
        this.activeBossUUID = boss.getUniqueId();

        broadcast(Component.text("Вы потревожили Верховного Инквизитора Малакая на втором этаже! Сразитесь с ним!", NamedTextColor.DARK_RED));
    }

    // --- ЛОГИКА ОТСЛЕЖИВАНИЯ СМЕРТЕЙ И СБРОСА ---

    // Вызывается из DungeonListener, когда ЛЮБОЙ игрок умирает на сервере
    public void handlePlayerDeath(Player player) {
        if (state == State.LOBBY || state == State.COMPLETED) return;

        // Если умерший игрок был участником зачистки данжа
        if (players.contains(player.getUniqueId())) {
            broadcast(Component.text("Участник " + player.getName() + " пал в бою!", NamedTextColor.RED));

            // Проверяем, остался ли в живых хоть КТО-ТО из команды данжа на сервере
            long alivePlayers = players.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline() && !p.isDead())
                    .count();

            // Если живых участников 0 — вся команда погибла, данж провален
            if (alivePlayers == 0) {
                Bukkit.broadcast(Component.text("[Данж] Группа искателей приключений полностью погибла! Замок Поборников восстанавливается...", NamedTextColor.RED));
                resetDungeon();
            }
        }
    }

    // Вызывается из DungeonListener, когда наш босс погибает
    public void handleBossCompleted(UUID deadEntityUUID) {
        if (state != State.BOSS) return;

        if (activeBossUUID != null && activeBossUUID.equals(deadEntityUUID)) {
            this.state = State.COMPLETED;
            Bukkit.broadcast(Component.text("[Данж] Верховный Инквизитор Малакай повержен! Данж успешно зачищен!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));

            // Даем игрокам, например, 30 секунд собрать выпавший лут, прежде чем обнулить постройку
            Bukkit.getScheduler().runTaskLater(plugin, this::resetDungeon, 600L); // 600 тиков = 30 сек
        }
    }

    // Полный сброс состояния сессии и регенерация карты
    private void resetDungeon() {
        // 1. Принудительно прячем BossBar лобби у всех (на случай экстренного сброса)
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(this.lobbyBossBar);
        }

        // 2. Вырезаем оставшихся мобов волны (если игроки умерли, а мобы живы)
        for (Entity entity : waveMonsters) {
            if (entity.isValid()) entity.remove();
        }

        // 3. Убиваем босса (если сброс произошел по смерти игроков)
        if (activeBossUUID != null) {
            Entity boss = Bukkit.getEntity(activeBossUUID);
            if (boss != null && boss.isValid()) boss.remove();
        }

        // 4. Очищаем списки данных
        players.clear();
        waveMonsters.clear();
        roomCreator = null;
        activeBossUUID = null;
        currentWave = 1;

        // 5. Просим DungeonManager восстановить исходные блоки замка из схематики (.schem)
        dungeonManager.pasteSchematic(origin);

        // 6. Возвращаем стейт в Лобби — теперь новые игроки могут запускать заново!
        this.state = State.LOBBY;
        plugin.getLogger().info("[Dungeon] Состояние данжа полностью обнулено. Блоки восстановлены.");
    }

    // Вспомогательные методы
    private ItemStack createDungeonKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        meta.displayName(Component.text("Ключ от Замка Поборников", NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(castleKey, PersistentDataType.BOOLEAN, true);
        key.setItemMeta(meta);
        return key;
    }

    private Location getOffsetLocation(String path) {
        int x = plugin.getConfig().getInt(path + ".x");
        int y = plugin.getConfig().getInt(path + ".y");
        int z = plugin.getConfig().getInt(path + ".z");
        return origin.clone().add(x, y, z);
    }

    private int getSafeInt(Object obj) {
        if (obj instanceof Number number) return number.intValue();
        return 0;
    }

    private boolean isInZone(Location loc, Location min, Location max) {
        // ИСПРАВЛЕНИЕ 1: Переходим на getBlockX/Y/Z() вместо getX/Y/Z().
        // Это заставит Java сравнивать номера блоков целиком, игнорируя микро-сдвиги и дроби!
        int playerX = loc.getBlockX();
        int playerY = loc.getBlockY();
        int playerZ = loc.getBlockZ();

        int minX = min.getBlockX();
        int minY = min.getBlockY();
        int minZ = min.getBlockZ();

        int maxX = max.getBlockX();
        // ИСПРАВЛЕНИЕ 2: Защита «от плоского лобби».
        // Если в конфиге maxY и minY равны (высота 0), мы искусственно даем запас +3 блока вверх,
        // чтобы хитбокс игрока (рост 2 блока) и его прыжки полностью помещались в лобби.
        int maxY = (max.getBlockY() == min.getBlockY()) ? max.getBlockY() + 3 : max.getBlockY();
        int maxZ = max.getBlockZ();

        return playerX >= minX && playerX <= maxX
                && playerY >= minY && playerY <= maxY
                && playerZ >= minZ && playerZ <= maxZ;
    }

    private void broadcast(Component message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    public State getState() { return state; }

    public void handlePlayerQuit(Player player) {
        if (players.contains(player.getUniqueId())) {
            players.remove(player.getUniqueId());
            player.hideBossBar(this.lobbyBossBar);
            broadcast(Component.text("Игрок " + player.getName() + " покинул мир данжа!", NamedTextColor.RED));

            // Если игра уже шла, проверяем, остался ли кто-то живой
            if (state != State.LOBBY && state != State.COMPLETED) {
                long alivePlayers = players.stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline() && !p.isDead())
                        .count();

                if (alivePlayers == 0) {
                    Bukkit.broadcast(Component.text("[Данж] Все игроки покинули зачистку. Карта восстанавливается...", NamedTextColor.RED));
                    resetDungeon();
                }
            }
            // Если ливнул в лобби, пересчитываем лидера комнаты
            else if (state == State.LOBBY) {
                if (roomCreator != null && roomCreator.equals(player.getUniqueId())) {
                    if (!players.isEmpty()) {
                        roomCreator = players.get(0); // Передаем лидерство следующему
                        updateLobbyBossBar();
                    } else {
                        roomCreator = null;
                    }
                } else {
                    updateLobbyBossBar();
                }
            }
        }
    }
}