package ru.politaboba.serverSystem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.politaboba.serverSystem.Dangeon.DungeonDevTool;
import ru.politaboba.serverSystem.Dangeon.PobornikCastle.*;
import ru.politaboba.serverSystem.bounty.BountyCommand;
import ru.politaboba.serverSystem.bounty.BountyListener;
import ru.politaboba.serverSystem.bounty.BountyOrder;
import ru.politaboba.serverSystem.bounty.BountyTabCompleter;
import ru.politaboba.serverSystem.contract.Agreement;
import ru.politaboba.serverSystem.contract.ContractCommand;
import ru.politaboba.serverSystem.contract.ContractListener;
import ru.politaboba.serverSystem.contract.ContractTabCompleter;
import ru.politaboba.serverSystem.faction.command.FactionCommand;
import ru.politaboba.serverSystem.faction.command.FactionTabCompleter;
import ru.politaboba.serverSystem.faction.command.GlobalChatCommand;
import ru.politaboba.serverSystem.faction.listener.FactionChatListener;
import ru.politaboba.serverSystem.faction.listener.FactionListener;
import ru.politaboba.serverSystem.faction.manager.ChatManager;
import ru.politaboba.serverSystem.faction.manager.FactionDataManager;
import ru.politaboba.serverSystem.faction.model.Faction;
import ru.politaboba.serverSystem.item.combat.SmokeBombListener;
import ru.politaboba.serverSystem.item.brewing.BarrelAgingListener;
import ru.politaboba.serverSystem.item.brewing.BrewingCauldronListener;
import ru.politaboba.serverSystem.item.brewing.BrewingStorageListener;
import ru.politaboba.serverSystem.item.combat.MercenaryArmorListener;
import ru.politaboba.serverSystem.item.cooking.CampfireCookingListener;
import ru.politaboba.serverSystem.item.food.FoodEatListener;
import ru.politaboba.serverSystem.item.food.FoodFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public final class ServerSystem extends JavaPlugin {

    private final Map<String, Faction> factions = new HashMap<>();
    private final Map<UUID, String> playerFactionMap = new HashMap<>();
    private final Map<UUID, BountyOrder> bountyOrders = new HashMap<>();
    private final List<Agreement> agreements = new ArrayList<>();
    private Scoreboard scoreboard;

    private FactionDataManager factionDataManager;
    private Connection connection;

    public FactionDataManager getFactionDataManager() {
        return factionDataManager;
    }

    /**
     * Возвращает активное подключение к базе данных.
     * Если соединение закрылось или упало по таймауту, оно будет автоматически пересоздано.
     */
    public Connection getDatabaseConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initDatabase();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    @Override
    public void onEnable() {

// Регистрация листенера артефакта (уже было у тебя)
        getServer().getPluginManager().registerEvents(new ItemArtifactListener(), this);

        // ==================== МОДУЛЬ КАСТОМНОГО ДАНЖА ====================

        // 1. Инициализируем механику кастомного босса
        ArchVindicatorBoss bossMechanics = new ArchVindicatorBoss(this);

        // 2. Инициализируем менеджер сессий данжей (управляет NBT-структурами и логикой)
        DungeonManager dungeonManager = new DungeonManager(this, bossMechanics);

        // 3. Регистрируем обновленную команду ручного спавна замка для админов
        if (this.getCommand("generatecastle") != null) {
            this.getCommand("generatecastle").setExecutor(new TestCastleCommand(dungeonManager));
        }

        // 4. Регистрируем главный игровой листенер данжа (кнопки старта, ключи, волны мобов)
        DungeonListener dungeonListener = new DungeonListener(this, dungeonManager);
        getServer().getPluginManager().registerEvents(dungeonListener, this);

        // 5. Инициализируем и регистрируем палочку-инструмент разработчика (Dev Tool)
        DungeonDevTool devTool = new DungeonDevTool();
        getServer().getPluginManager().registerEvents(devTool, this); // Регистрируем как листенер кликов

        if (this.getCommand("dungeonorigin") != null) {
            this.getCommand("dungeonorigin").setExecutor(devTool); // Регистрируем команду установки точки отсчета
        }

        if (this.getCommand("dungeon") != null) {
            this.getCommand("dungeon").setExecutor(new DungeonPlayerCommand(dungeonManager));
        }

        //--------------------------

        // Создаем config.yml с дефолтными настройками, если его не было
        saveDefaultConfig();

        // 1. Инициализируем подключение к базе данных хостинга
        initDatabase();

        // 2. Инициализируем менеджер данных и загружаем фракции/контракты/розыски прямо из БД
        this.factionDataManager = new FactionDataManager(this);
        this.factionDataManager.loadAll();

        // 3. Запускаем СИНХРОННЫЙ таймер автосохранения.
        // Он безопасно берет снимки измененных фракций в главном потоке и отправляет SQL запросы в асинхрон
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (this.factionDataManager != null) {
                this.factionDataManager.saveAll();
            }
        }, 12000L, 12000L); // Раз в 10 минут (12000 тиков)

        // Инициализация модуля арестов (Кандалы)
        ru.politaboba.serverSystem.arrest.ArrestManager arrestManager = new ru.politaboba.serverSystem.arrest.ArrestManager(this);
        getServer().getPluginManager().registerEvents(new ru.politaboba.serverSystem.arrest.ArrestListener(arrestManager, this), this);

        if (getCommand("handcuffs") != null) {
            getCommand("handcuffs").setExecutor(new ru.politaboba.serverSystem.arrest.ArrestCommand());
        }

        // Инициализация чат-менеджера и глобальных команд общения
        ChatManager chatManager = new ChatManager();
        getServer().getPluginManager().registerEvents(new FactionChatListener(this, chatManager), this);

        GlobalChatCommand chatCommand = new GlobalChatCommand(this, chatManager);
        if (getCommand("g") != null) getCommand("g").setExecutor(chatCommand);
        if (getCommand("msg") != null) getCommand("msg").setExecutor(chatCommand);

        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Регистрируем команду /f и автотабкомплитер
        if (getCommand("faction") != null) {
            FactionCommand factionCommand = new FactionCommand(this);
            getCommand("faction").setExecutor(factionCommand);
            getCommand("faction").setTabCompleter(new FactionTabCompleter(this));
        }

        // Обработчик событий фракций (GUI, обновление ников при входе)
        getServer().getPluginManager().registerEvents(new FactionListener(this), this);

        // Система охоты за головами (Bounty)
        if (getCommand("bounty") != null) {
            getCommand("bounty").setExecutor(new BountyCommand(this));
            getCommand("bounty").setTabCompleter(new BountyTabCompleter(this));
        }
        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        // Система нотариальных контрактов
        ContractCommand contractCommand = new ContractCommand(this);
        if (getCommand("contract") != null) {
            getCommand("contract").setExecutor(contractCommand);
            getCommand("contract").setTabCompleter(new ContractTabCompleter(this));
        }
        getServer().getPluginManager().registerEvents(new ContractListener(this, contractCommand), this);

        // Обработчики событий кастомных предметов
        getServer().getPluginManager().registerEvents(new SmokeBombListener(this), this);
        getServer().getPluginManager().registerEvents(new FoodEatListener(this), this);
        getServer().getPluginManager().registerEvents(new CampfireCookingListener(this), this);
        getServer().getPluginManager().registerEvents(new BrewingStorageListener(this), this);
        getServer().getPluginManager().registerEvents(new BrewingCauldronListener(this), this);
        getServer().getPluginManager().registerEvents(new BarrelAgingListener(this), this);
        getServer().getPluginManager().registerEvents(new MercenaryArmorListener(this), this);

        // Регистрация кастомных кулинарных рецептов
        FoodFactory foodFactory = new FoodFactory(this);
        foodFactory.registerAllCooking();

        // Регистрация рецептов верстака
        registerHandcuffsRecipe();
        registerSmokeBombRecipe();
        registerHalberdRecipe();
        registerMercenaryTemplateRecipe();

        getLogger().info("=======================================");
        getLogger().info(" [ServerSystem] Все RP модули успешно запущены!");
        getLogger().info(" [ServerSystem] Режим хранения: MySQL");
        getLogger().info("=======================================");
    }

    @Override
    public void onDisable() {
        // Принудительное СИНХРОННОЕ сохранение всех данных в БД перед полной остановкой сервера
        if (this.factionDataManager != null) {
            this.factionDataManager.saveAllSynchronously();
        }

        // Закрываем пул соединения с базой данных
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info("[ServerSystem] Соединение с MySQL успешно закрыто.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Очищаем команды из скорборда, чтобы избежать багов при перезагрузке плагина
        for (String factionName : factions.keySet()) {
            Team team = scoreboard.getTeam(factionName);
            if (team != null) team.unregister();

            Team enemyTeam = scoreboard.getTeam(factionName + "_enemies");
            if (enemyTeam != null) enemyTeam.unregister();
        }
    }

    private void initDatabase() {
        try {
            // Загружаем драйвер SQLite (он встроен во все ядра Spigot/Paper по умолчанию)
            Class.forName("org.sqlite.JDBC");

            // Создаем файл базы данных прямо в папке плагина: plugins/ServerSystem/database.db
            java.io.File dataFolder = new java.io.File(getDataFolder(), "database.db");

            String url = "jdbc:sqlite:" + dataFolder.getAbsolutePath();
            this.connection = DriverManager.getConnection(url);

            getLogger().info("[SQLite] Успешно подключено к локальному файлу базы данных!");
        } catch (ClassNotFoundException e) {
            getLogger().severe("[SQLite] Драйвер SQLite не найден!");
            e.printStackTrace();
        } catch (SQLException e) {
            getLogger().severe("[SQLite] Не удалось инициализировать файл базы данных!");
            e.printStackTrace();
        }
    }

    public Map<String, Faction> getFactions() { return factions; }
    public Map<UUID, String> getPlayerFactionMap() { return playerFactionMap; }
    public Map<UUID, BountyOrder> getBountyOrders() { return bountyOrders; }
    public List<Agreement> getAgreements() { return agreements; }

    /**
     * Обновление отображения ников (Государство в табе, полная инфа над головой в игре)
     */
    public void updateAllPlayersDisplay() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = onlinePlayer.getUniqueId();
            String factionName = playerFactionMap.get(playerUUID);

            if (factionName != null) {
                Faction faction = factions.get(factionName);
                if (faction == null) continue;

                ChatColor color = faction.getFactionColor();
                String prefixTag = factionName.substring(0, Math.min(factionName.length(), 4));

                // 1. НАД ГОЛОВОЙ В ИГРЕ
                String fullRoleString = faction.getPlayerInfoString(playerUUID);
                String overheadPrefix = ChatColor.DARK_GRAY + "[" + color + prefixTag + ChatColor.DARK_GRAY + "] "
                        + ChatColor.RESET + fullRoleString + ChatColor.RESET + " ";

                Team team = scoreboard.getTeam(onlinePlayer.getName());
                if (team == null) team = scoreboard.registerNewTeam(onlinePlayer.getName());

                team.setPrefix(overheadPrefix);
                team.addEntry(onlinePlayer.getName());

                // 2. В ТАБ-ЛИСТЕ
                String tabName = ChatColor.DARK_GRAY + "[" + color + prefixTag + ChatColor.DARK_GRAY + "] " + color + onlinePlayer.getName();
                onlinePlayer.setPlayerListName(tabName);

                // 3. СИСТЕМА ЧЕРНОГО СПИСКА (Враги государства)
                Team enemyTeam = scoreboard.getTeam(factionName + "_enemies");
                if (enemyTeam == null) {
                    enemyTeam = scoreboard.registerNewTeam(factionName + "_enemies");
                    enemyTeam.setColor(ChatColor.DARK_RED);
                }
                if (faction.getBlacklist() != null) {
                    for (UUID enemyUUID : faction.getBlacklist()) {
                        Player enemyPlayer = Bukkit.getPlayer(enemyUUID);
                        if (enemyPlayer != null && enemyPlayer.isOnline()) {
                            enemyTeam.addEntry(enemyPlayer.getName());
                        }
                    }
                }
            } else {
                onlinePlayer.setPlayerListName(ChatColor.GRAY + "[Скиталец] " + ChatColor.WHITE + onlinePlayer.getName());

                Team playerTeam = scoreboard.getTeam(onlinePlayer.getName());
                if (playerTeam != null) {
                    playerTeam.removeEntry(onlinePlayer.getName());
                    playerTeam.unregister();
                }
            }
        }
    }

    private void registerHandcuffsRecipe() {
        ItemStack cuffs = ru.politaboba.serverSystem.arrest.ArrestManager.createHandcuffs();
        NamespacedKey key = new NamespacedKey(this, "rp_handcuffs");
        org.bukkit.inventory.ShapedRecipe recipe = new org.bukkit.inventory.ShapedRecipe(key, cuffs);

        recipe.shape("BNB", "B B", "   ");
        recipe.setIngredient('B', Material.IRON_BARS);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);

        Bukkit.addRecipe(recipe);
    }

    private void registerSmokeBombRecipe() {
        ru.politaboba.serverSystem.item.ItemFactory factory = new ru.politaboba.serverSystem.item.ItemFactory(this);
        ItemStack smokeBomb = factory.createSmokeBomb();
        NamespacedKey key = new NamespacedKey(this, "rp_smoke_bomb");
        org.bukkit.inventory.ShapelessRecipe recipe = new org.bukkit.inventory.ShapelessRecipe(key, smokeBomb);

        recipe.addIngredient(Material.GUNPOWDER);
        recipe.addIngredient(Material.COAL);
        recipe.addIngredient(Material.FIREWORK_ROCKET);
        recipe.addIngredient(Material.IRON_NUGGET);

        Bukkit.addRecipe(recipe);
    }

    private void registerHalberdRecipe() {
        ru.politaboba.serverSystem.item.ItemFactory factory = new ru.politaboba.serverSystem.item.ItemFactory(this);
        ItemStack halberd = factory.createHalberd();
        NamespacedKey key = new NamespacedKey(this, "rp_halberd");
        org.bukkit.inventory.ShapedRecipe recipe = new org.bukkit.inventory.ShapedRecipe(key, halberd);

        recipe.shape("DBD", " S ", " S ");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('B', Material.DIAMOND_BLOCK);
        recipe.setIngredient('S', Material.BREEZE_ROD);

        Bukkit.addRecipe(recipe);
    }

    private void registerMercenaryTemplateRecipe() {
        ItemStack template = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        org.bukkit.inventory.meta.ItemMeta meta = template.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4§lКузнечный Шаблон: Улучшение Наёмника");
            List<String> lore = new ArrayList<>();
            lore.add("§7Позволяет модифицировать броню под нужды наёмников.");
            lore.add("");
            lore.add("§eПрименимо в кузнечном столе:");
            lore.add(" §7• Объедините с элементом брони");
            lore.add(" §7• Требуется: §b1 Алмаз§7 в качестве материала");
            lore.add("");
            lore.add("§c§lПолный сет экипировки наёмника даёт:");
            lore.add(" §e• §aСкорость II (+20% к бегу)");
            lore.add(" §e• §aВыносливость (+3 доп. сердца)");
            lore.add(" §e• §aБоевая регенерация (Регенерация I)");
            meta.setLore(lore);

            NamespacedKey key = new NamespacedKey(this, "mercenary_template");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            template.setItemMeta(meta);
        }

        NamespacedKey recipeKey = new NamespacedKey(this, "mercenary_template_craft");
        org.bukkit.inventory.ShapedRecipe recipe = new org.bukkit.inventory.ShapedRecipe(recipeKey, template);

        recipe.shape("LTL", "LNL", "LLL");
        recipe.setIngredient('L', Material.LEATHER);
        recipe.setIngredient('T', Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);

        Bukkit.addRecipe(recipe);
    }
}