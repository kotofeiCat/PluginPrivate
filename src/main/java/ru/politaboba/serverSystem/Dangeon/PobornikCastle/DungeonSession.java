package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DungeonSession {

    public enum State { LOBBY, WAVES, LOOTING, BOSS, COMPLETED }

    private final JavaPlugin plugin;
    private final Location origin;
    private final ArchVindicatorBoss bossMechanics;
    private final DungeonManager dungeonManager;

    private State state = State.LOBBY;
    private final List<UUID> players = new ArrayList<>();
    private final Set<UUID> waveMonsterUUIDs = new HashSet<>();
    private final List<WaveConfig> hardcodedWaves = new ArrayList<>();
    private int currentWave = 1;

    private UUID roomCreator = null;
    private UUID activeBossUUID = null;
    private final int MAX_PLAYERS = 5;
    private final BossBar lobbyBossBar;

    private final NamespacedKey waveMobKey;
    private final NamespacedKey castleKey;

    // 2. Инициализируем волны прямо в конструкторе DungeonSession
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

        // НАСТРОЙКА ВОЛН ПРЯМО В КОДЕ (Забудь про YAML-ошибки):
        // Волна 1: 5 Зомби, 2 Скелета
        hardcodedWaves.add(new WaveConfig()
                .add(EntityType.ZOMBIE, 5)
                .add(EntityType.SKELETON, 2));

        // Волна 2: 4 Зомби, 3 Поборника, 4 Скелета
        hardcodedWaves.add(new WaveConfig()
                .add(EntityType.ZOMBIE, 4)
                .add(EntityType.VINDICATOR, 3)
                .add(EntityType.SKELETON, 4));

        // Волна 3: 5 Поборников, 3 Визер-Скелета
        hardcodedWaves.add(new WaveConfig()
                .add(EntityType.VINDICATOR, 5)
                .add(EntityType.WITHER_SKELETON, 3));
    }

    public void handlePlayerReady(Player player) {
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
        this.lobbyBossBar.progress(Math.min(1.0f, Math.max(0.0f, progress)));
        String creatorName = roomCreator != null ? Bukkit.getOfflinePlayer(roomCreator).getName() : "Нет";
        if (creatorName == null) creatorName = "Неизвестно";
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
        Location spawnLoc = getOffsetLocation("dungeon.stage1-spawn").add(0.5, 1.0, 0.5);

        clearLobbyBossBar();
        broadcast(Component.text("Лидер запустил игру! Защитите комнату от волн поборников!", NamedTextColor.RED).decorate(TextDecoration.BOLD));

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.teleport(spawnLoc);
        }

        if (spawnLoc.getWorld() != null) {
            // Предзагрузка чанка спавна игроков
            spawnLoc.getWorld().getChunkAtAsync(spawnLoc).thenRun(() -> {
                Bukkit.getScheduler().runTaskLater(plugin, this::spawnWave, 40L);
            });
        }
    }

    public void spawnWave() {
        if (state != State.WAVES) return;

        // Так как индекс в List начинается с 0, первая волна — это currentWave - 1
        int waveIndex = currentWave - 1;

        // Если прошли все запрограммированные волны
        if (waveIndex >= hardcodedWaves.size()) {
            endWaveStage();
            return;
        }

        // Точки спавна (координаты смещения) всё так же берем из конфига
        List<Map<?, ?>> spawnOffsets = plugin.getConfig().getMapList("dungeon.wave-spawns");
        if (spawnOffsets.isEmpty()) {
            plugin.getLogger().severe("[Dungeon] ОШИБКА: Список 'dungeon.wave-spawns' пуст! Используем фолбэк-точку (0,0,0)");
        }

        Random random = new Random();
        broadcast(Component.text("⚠️ ВОЛНА " + currentWave + " НАЧАЛАСЬ! Защищайтесь!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        World world = origin.getWorld();
        if (world == null) return;

        // Достаем конфигурацию текущей волны из Java-кода
        WaveConfig currentWaveConfig = hardcodedWaves.get(waveIndex);

        int totalAttempted = 0;
        int successfullySpawned = 0;

        // Перебираем жестко прописанную Map напрямую
        for (Map.Entry<EntityType, Integer> entry : currentWaveConfig.getMobs().entrySet()) {
            EntityType type = entry.getKey();
            int count = entry.getValue();

            plugin.getLogger().info("[Dungeon] Код-Спавн волны " + currentWave + ": " + type.name() + " x" + count);

            for (int i = 0; i < count; i++) {
                totalAttempted++;
                Location loc;

                if (!spawnOffsets.isEmpty()) {
                    Map<?, ?> randomOffset = spawnOffsets.get(random.nextInt(spawnOffsets.size()));
                    loc = origin.clone().add(
                            getSafeDouble(randomOffset.get("x")),
                            getSafeDouble(randomOffset.get("y")),
                            getSafeDouble(randomOffset.get("z"))
                    );
                } else {
                    // Если даже смещений нет в конфиге, спавним прямо на origin + смещение по Z
                    loc = origin.clone().add(0, 0, 13);
                }

                // Рандомизация позиции в радиусе
                double rx = (random.nextDouble() - 0.5) * 2.5;
                double rz = (random.nextDouble() - 0.5) * 2.5;
                loc.add(rx, 1.5, rz); // Безопасный подъем по Y

                // Принудительная подгрузка чанка
                if (!loc.getChunk().isLoaded()) {
                    loc.getChunk().load();
                }

                Entity spawned = world.spawnEntity(loc, type);
                if (spawned != null) {
                    spawned.getPersistentDataContainer().set(waveMobKey, PersistentDataType.BOOLEAN, true);

                    if (spawned instanceof Monster monster) {
                        monster.setRemoveWhenFarAway(false);
                        setupCustomMob(monster);
                    }
                    waveMonsterUUIDs.add(spawned.getUniqueId());
                    successfullySpawned++;
                }
            }
        }

        plugin.getLogger().info("[Dungeon] Результат волны " + currentWave + " -> Попыток: " + totalAttempted + ", Создано: " + successfullySpawned);

        // Фолбэк-проверка (если мобов заблокировал сторонний плагин вроде WorldGuard)
        if (waveMonsterUUIDs.isEmpty()) {
            plugin.getLogger().warning("[Dungeon] Мобы не появились физически. Проверь регионы WorldGuard! Пропуск.");
            currentWave++;
            Bukkit.getScheduler().runTaskLater(plugin, this::spawnWave, 60L);
        }
    }

    private void setupCustomMob(Monster monster) {
        monster.setCustomNameVisible(true);
        EntityEquipment equip = monster.getEquipment();

        switch (monster.getType()) {
            case ZOMBIE -> {
                monster.customName(Component.text("Чумной Воитель [Волна " + currentWave + "]", NamedTextColor.RED));
                setMobMaxHealth(monster, 40.0);
                setMobSpeed(monster, 0.28);
                if (equip != null) {
                    equip.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                    equip.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
            }
            case SKELETON -> {
                monster.customName(Component.text("Замковый Лучник [Волна " + currentWave + "]", NamedTextColor.GRAY));
                setMobMaxHealth(monster, 25.0);
                if (equip != null) {
                    equip.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                }
            }
            case VINDICATOR -> {
                monster.customName(Component.text("Элитный Палач [Волна " + currentWave + "]", NamedTextColor.GOLD));
                setMobMaxHealth(monster, 60.0);
                setMobSpeed(monster, 0.32);
                if (equip != null) {
                    equip.setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
                }
            }
            case WITHER_SKELETON -> {
                monster.customName(Component.text("Гвардеец Преисподней [Волна " + currentWave + "]", NamedTextColor.DARK_RED));
                setMobMaxHealth(monster, 50.0);
                if (equip != null) {
                    equip.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    equip.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                }
            }
            default -> monster.customName(Component.text("Рейдер Замка [Волна " + currentWave + "]", NamedTextColor.RED));
        }

        Player nearestTarget = findNearestDungeonPlayer(monster.getLocation());
        if (nearestTarget != null) {
            monster.setTarget(nearestTarget);
        }
    }

    private void setMobMaxHealth(Monster monster, double health) {
        AttributeInstance attr = monster.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(health);
            monster.setHealth(health);
        }
    }

    private void setMobSpeed(Monster monster, double speed) {
        AttributeInstance attr = monster.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(speed);
        }
    }

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

        waveMonsterUUIDs.remove(entity.getUniqueId());

        if (waveMonsterUUIDs.isEmpty()) {
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

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().addItem(createDungeonKey());
                break; // Ключ выдается только одному (первому онлайн) игроку сессии
            }
        }
        generateLootTables();
    }

    public boolean tryOpenDoor(Block clickedBlock) {
        // Разрешаем обрабатывать клик в стейтах LOOTING и BOSS
        if (state != State.LOOTING && state != State.BOSS) {
            plugin.getLogger().info("[Dungeon] Отмена открытия ворот. Неподходящий стейт: " + state.name());
            return false;
        }

        List<Map<?, ?>> doorOffsets = plugin.getConfig().getMapList("dungeon.doors-stage1");
        if (doorOffsets.isEmpty()) {
            plugin.getLogger().severe("[Dungeon] ОШИБКА: Список дверей 'dungeon.doors-stage1' пуст в конфиге!");
            return false;
        }

        boolean nextToDoor = false;
        Location clickedLoc = clickedBlock.getLocation();

        // Проверяем, действительно ли игрок кликнул по блоку рядом с зоной дверей
        for (Map<?, ?> offset : doorOffsets) {
            Location doorLoc = origin.clone().add(
                    getSafeDouble(offset.get("x")),
                    getSafeDouble(offset.get("y")),
                    getSafeDouble(offset.get("z"))
            );

            // Радиус 5.0 блоков — берем с запасом, чтобы точно сработало
            if (doorLoc.getWorld().equals(clickedLoc.getWorld()) && doorLoc.distance(clickedLoc) <= 5.0) {
                nextToDoor = true;
                break;
            }
        }

        if (nextToDoor) {
            plugin.getLogger().info("[Dungeon] Дверь опознана! Начинаем принудительный снос блоков ворот...");

            // Сносим блоки дверей строго по координатам из конфига
            for (Map<?, ?> offset : doorOffsets) {
                Location loc = origin.clone().add(
                        getSafeDouble(offset.get("x")),
                        getSafeDouble(offset.get("y")),
                        getSafeDouble(offset.get("z"))
                );

                Block doorPart = loc.getBlock();

                // Эффекты разрушения блоков
                if (doorPart.getType() != Material.AIR) {
                    doorPart.getWorld().spawnParticle(
                            org.bukkit.Particle.BLOCK,
                            doorPart.getLocation().add(0.5, 0.5, 0.5),
                            15,
                            doorPart.getBlockData()
                    );
                }

                // Агрессивный снос: превращаем в воздух сам блок, а также блоки строго НАД и ПОД ним
                // Это решает проблему с двухблочными железными дверями и решетками
                doorPart.setType(Material.AIR);
                doorPart.getRelative(org.bukkit.block.BlockFace.UP).setType(Material.AIR);
                doorPart.getRelative(org.bukkit.block.BlockFace.DOWN).setType(Material.AIR);
            }

            // Звуки открытия ворот
            clickedBlock.getWorld().playSound(clickedLoc, org.bukkit.Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.7f);
            clickedBlock.getWorld().playSound(clickedLoc, org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.5f);

            // Спавним босса ТОЛЬКО если мы еще не переключились в этот стейт ранее
            if (this.state != State.BOSS) {
                this.state = State.BOSS;
                spawnBossStage();
            } else {
                plugin.getLogger().info("[Dungeon] Босс уже был заспавнен ранее. Просто открываем проход.");
            }

            return true;
        }

        plugin.getLogger().warning("[Dungeon] Игрок кликнул по кнопке/двери, но локация блока " + clickedLoc.toVector() + " слишком далеко от дверей в конфиге!");
        return false;
    }

    private void generateLootTables() {
        List<Map<?, ?>> chestOffsets = plugin.getConfig().getMapList("dungeon.loot-chests");
        if (chestOffsets.isEmpty()) {
            plugin.getLogger().severe("[Dungeon] ОШИБКА ЛУТА: Список 'dungeon.loot-chests' пуст!");
            return;
        }

        Random random = new Random();

        // Получаем реестр зачарований
        Enchantment sharpness = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
        Enchantment protection = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
        Enchantment power = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
        Enchantment unbreaking = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));

        plugin.getLogger().info("[Dungeon] Распределение категорий лута для " + chestOffsets.size() + " сундуков...");

        for (Map<?, ?> offset : chestOffsets) {
            Location loc = origin.clone().add(
                    getSafeDouble(offset.get("x")),
                    getSafeDouble(offset.get("y")),
                    getSafeDouble(offset.get("z"))
            );

            if (!loc.getChunk().isLoaded()) {
                loc.getChunk().load();
            }

            Block block = loc.getBlock();

            // Гарантируем, что там стоит контейнер (сундук)
            if (block.getType() != Material.CHEST && block.getType() != Material.BARREL) {
                block.setType(Material.CHEST, false);
            }

            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Chest chest) {
                org.bukkit.inventory.Inventory inv = chest.getSnapshotInventory();
                inv.clear(); // Очищаем старый лут подчистую

                // Роллим категорию сундука (от 0.0 до 1.0)
                double tierRoll = random.nextDouble();
                String tierName;
                NamedTextColor tierColor;
                int itemsCount;

                if (tierRoll < 0.55) {
                    // ==========================================
                    // 1. ПЛОХОЙ СУНДУК (Шанс 55% -> от 0.0 до 0.55)
                    // ==========================================
                    tierName = "ПЛОХОЙ СУНДУК";
                    tierColor = NamedTextColor.GRAY;
                    itemsCount = 3 + random.nextInt(3); // 3-5 предметов

                    for (int i = 0; i < itemsCount; i++) {
                        int slot = random.nextInt(inv.getSize());
                        if (inv.getItem(slot) != null) continue;

                        double itemRoll = random.nextDouble();
                        if (itemRoll < 0.25) {
                            inv.setItem(slot, new ItemStack(Material.ROTTEN_FLESH, 2 + random.nextInt(4)));
                        } else if (itemRoll < 0.50) {
                            inv.setItem(slot, new ItemStack(Material.BREAD, 1 + random.nextInt(3)));
                        } else if (itemRoll < 0.70) {
                            inv.setItem(slot, new ItemStack(Material.COAL, 3 + random.nextInt(5)));
                        } else if (itemRoll < 0.90) {
                            inv.setItem(slot, new ItemStack(Material.IRON_INGOT, 1 + random.nextInt(3)));
                        } else {
                            inv.setItem(slot, new ItemStack(Material.BONE, 2));
                        }
                    }

                } else if (tierRoll < 0.90) {
                    // ==========================================
                    // 2. НОРМАЛЬНЫЙ СУНДУК (Шанс 35% -> от 0.55 до 0.90)
                    // ==========================================
                    tierName = "НОРМАЛЬНЫЙ СУНДУК";
                    tierColor = NamedTextColor.GOLD;
                    itemsCount = 4 + random.nextInt(4); // 4-7 предметов

                    for (int i = 0; i < itemsCount; i++) {
                        int slot = random.nextInt(inv.getSize());
                        if (inv.getItem(slot) != null) continue;

                        double itemRoll = random.nextDouble();
                        if (itemRoll < 0.15) {
                            inv.setItem(slot, new ItemStack(Material.DIAMOND_SWORD));
                        } else if (itemRoll < 0.35) {
                            inv.setItem(slot, new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(2)));
                        } else if (itemRoll < 0.60) {
                            inv.setItem(slot, new ItemStack(Material.DIAMOND, 1 + random.nextInt(2)));
                        } else if (itemRoll < 0.85) {
                            inv.setItem(slot, new ItemStack(Material.GOLD_INGOT, 4 + random.nextInt(4)));
                        } else {
                            inv.setItem(slot, new ItemStack(Material.LAPIS_LAZULI, 6));
                        }
                    }

                } else {
                    // ==========================================
                    // 3. ОТЛИЧНЫЙ СУНДУК (Шанс 10% -> от 0.90 до 1.0)
                    // ==========================================
                    tierName = "ОТЛИЧНЫЙ СУНДУК";
                    tierColor = NamedTextColor.AQUA;
                    itemsCount = 4 + random.nextInt(3); // 4-6 предметов

                    for (int i = 0; i < itemsCount; i++) {
                        int slot = random.nextInt(inv.getSize());
                        if (inv.getItem(slot) != null) continue;

                        double itemRoll = random.nextDouble();
                        if (itemRoll < 0.15) {
                            inv.setItem(slot, new ItemStack(Material.NETHERITE_INGOT, 1));
                        } else if (itemRoll < 0.40) {
                            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                            if (meta != null) {
                                int bookType = random.nextInt(3);
                                if (bookType == 0 && protection != null) {
                                    meta.addStoredEnchant(protection, 6, true);
                                    meta.displayName(Component.text("Забытый фолиант: Защита VI", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                                } else if (bookType == 1 && sharpness != null) {
                                    meta.addStoredEnchant(sharpness, 6, true);
                                    meta.displayName(Component.text("Древний манускрипт: Острота VI", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                                } else if (bookType == 2 && power != null) {
                                    meta.addStoredEnchant(power, 6, true);
                                    meta.displayName(Component.text("Эльфийские писания: Сила VI", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                                }
                                book.setItemMeta(meta);
                                inv.setItem(slot, book);
                            }
                        } else if (itemRoll < 0.70) {
                            Material[] armorTypes = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
                            ItemStack armor = new ItemStack(armorTypes[random.nextInt(armorTypes.length)]);
                            ItemMeta meta = armor.getItemMeta();
                            if (meta != null) {
                                if (protection != null) meta.addEnchant(protection, 4, true);
                                if (unbreaking != null) meta.addEnchant(unbreaking, 3, true);
                                meta.displayName(Component.text("Доспех Хранителя Замка", NamedTextColor.DARK_PURPLE));
                                armor.setItemMeta(meta);
                            }
                            inv.setItem(slot, armor);
                        } else {
                            inv.setItem(slot, new ItemStack(Material.EMERALD, 4 + random.nextInt(5)));
                        }
                    }
                }

                // Обновляем отображение имени контейнера
                chest.customName(Component.text(tierName, tierColor, TextDecoration.BOLD));
                chest.update(true, false);
                plugin.getLogger().info("[Dungeon] Сгенерирован " + tierName + " на координатах " + loc.toVector());
            }
        }
    }

    private void spawnBossStage() {
        Location bossLoc = getOffsetLocation("dungeon.boss-spawn");
        if (!bossLoc.getChunk().isLoaded()) {
            bossLoc.getChunk().load();
        }
        Evoker boss = bossMechanics.spawn(bossLoc);
        if (boss != null) {
            this.activeBossUUID = boss.getUniqueId();
        }
        broadcast(Component.text("Вы потревожили Верховного Инквизитора Малакая на втором этаже! Сразитесь с ним!", NamedTextColor.DARK_RED));
    }

    public void handlePlayerDeath(Player player) {
        if (state == State.LOBBY || state == State.COMPLETED) return;

        if (players.contains(player.getUniqueId())) {
            broadcast(Component.text("Участник " + player.getName() + " пал в бою!", NamedTextColor.RED));

            long alivePlayers = players.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline() && !p.isDead())
                    .count();

            if (alivePlayers == 0) {
                Bukkit.broadcast(Component.text("[Данж] Группа искателей приключений полностью погибла! Восстановление...", NamedTextColor.RED));
                resetDungeon();
            }
        }
    }

    public void handleBossCompleted(UUID deadEntityUUID) {
        if (state != State.BOSS) return;

        if (activeBossUUID != null && activeBossUUID.equals(deadEntityUUID)) {
            this.state = State.COMPLETED;

            // 1. Объявляем на весь сервер о победе
            Bukkit.broadcast(Component.text("[Данж] Верховный Инквизитор Малакай повержен!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));

            // 2. Красивый бабах/салют на месте смерти босса (опционально, для атмосферы)
            Location bossLoc = getOffsetLocation("dungeon.boss-spawn");
            if (bossLoc.getWorld() != null) {
                bossLoc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, bossLoc.add(0, 1, 0), 100, 0.5, 0.5, 0.5, 0.5);
                bossLoc.getWorld().playSound(bossLoc, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // 3. Выводим титры на экран всем участникам данжа с обратным отсчетом
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                            Component.text("ПОБЕДА!", NamedTextColor.GOLD, TextDecoration.BOLD),
                            Component.text("Телепортация в лобби через 7 секунд...", NamedTextColor.GRAY)
                    ));
                }
            }

            // 4. ТАЙМЕР НА ТЕЛЕПОРТАЦИЮ (Через 7 секунд = 140 тиков)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Получаем центральную точку лобби (или минимальную точку лобби-зоны)
                Location lobbyLoc = getOffsetLocation("dungeon.lobby-zone.min").add(0.5, 1.0, 0.5);

                broadcast(Component.text("Зачистка завершена! Возвращение в безопасную зону...", NamedTextColor.YELLOW));

                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        // Возвращаем режим выживания/приключения, если они меняли, и телепортируем
                        p.teleport(lobbyLoc);
                        p.playSound(lobbyLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            }, 140L); // 140 тиков = 7 секунд. Можешь поставить своё число (например, 100L = 5 секунд)

            // 5. ПОЛНЫЙ СБРОС ДАНЖА И РЕГЕН СХЕМАТИКИ (Делаем чуть позже телепортации, например через 10 секунд = 200 тиков)
            // Это нужно, чтобы чанки успели разгрузиться, а игроки улетели до того, как WorldEdit начнет заменять блоки под ногами
            Bukkit.getScheduler().runTaskLater(plugin, this::resetDungeon, 200L);
        }
    }

    private void clearLobbyBossBar() {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.hideBossBar(this.lobbyBossBar);
            }
        }
    }

    private void resetDungeon() {
        clearLobbyBossBar();

        for (UUID mobUUID : waveMonsterUUIDs) {
            Entity entity = Bukkit.getEntity(mobUUID);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        waveMonsterUUIDs.clear();

        if (activeBossUUID != null) {
            Entity boss = Bukkit.getEntity(activeBossUUID);
            if (boss != null && boss.isValid()) boss.remove();
        }

        players.clear();
        roomCreator = null;
        activeBossUUID = null;
        currentWave = 1;

        dungeonManager.pasteSchematic(origin);
        this.state = State.LOBBY;
    }

    private ItemStack createDungeonKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Ключ от Замка Поборников", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(castleKey, PersistentDataType.BOOLEAN, true);
            key.setItemMeta(meta);
        }
        return key;
    }

    private Location getOffsetLocation(String path) {
        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        return origin.clone().add(x, y, z);
    }

    private double getSafeDouble(Object obj) {
        if (obj instanceof Number number) return number.doubleValue();
        return 0.0;
    }

    private boolean isInZone(Location loc, Location min, Location max) {
        int playerX = loc.getBlockX();
        int playerY = loc.getBlockY();
        int playerZ = loc.getBlockZ();

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());

        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        if (minY == maxY) maxY += 3;

        return playerX >= minX && playerX <= maxX
                && playerY >= minY && playerY <= maxY
                && playerZ >= minZ && playerZ <= maxZ;
    }

    private void broadcast(Component message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    public State getState() { return state; }

    public void handlePlayerQuit(Player player) {
        if (players.contains(player.getUniqueId())) {
            players.remove(player.getUniqueId());
            player.hideBossBar(this.lobbyBossBar);
            broadcast(Component.text("Игрок " + player.getName() + " покинул мир данжа!", NamedTextColor.RED));

            if (state != State.LOBBY && state != State.COMPLETED) {
                long alivePlayers = players.stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline() && !p.isDead())
                        .count();

                if (alivePlayers == 0) {
                    resetDungeon();
                }
            } else if (state == State.LOBBY) {
                if (roomCreator != null && roomCreator.equals(player.getUniqueId())) {
                    if (!players.isEmpty()) {
                        roomCreator = players.get(0);
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

    // Удобный контейнер для описания состава одной волны
    private static class WaveConfig {
        private final Map<EntityType, Integer> mobs = new HashMap<>();

        public WaveConfig add(EntityType type, int count) {
            mobs.put(type, count);
            return this;
        }

        public Map<EntityType, Integer> getMobs() {
            return mobs;
        }
    }
}
