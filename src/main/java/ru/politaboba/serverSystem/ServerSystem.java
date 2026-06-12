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
import ru.politaboba.serverSystem.cases.CaseCommand;
import ru.politaboba.serverSystem.cases.CaseListener;
import ru.politaboba.serverSystem.cases.CaseManager;
import ru.politaboba.serverSystem.cases.CaseTabCompleter;
import ru.politaboba.serverSystem.contract.Agreement;
import ru.politaboba.serverSystem.contract.ContractCommand;
import ru.politaboba.serverSystem.contract.ContractListener;
import ru.politaboba.serverSystem.contract.ContractTabCompleter;
import ru.politaboba.serverSystem.faction.command.FactionCommand;
import ru.politaboba.serverSystem.faction.command.FactionTabCompleter;
import ru.politaboba.serverSystem.faction.command.GlobalChatCommand;
import ru.politaboba.serverSystem.faction.command.MsgTabCompleter;
import ru.politaboba.serverSystem.faction.listener.FactionChatListener;
import ru.politaboba.serverSystem.faction.listener.FactionListener;
import ru.politaboba.serverSystem.faction.manager.ChatManager;
import ru.politaboba.serverSystem.faction.manager.FactionDataManager;
import ru.politaboba.serverSystem.faction.model.Faction;
import ru.politaboba.serverSystem.item.combat.SmokeBombListener;
import ru.politaboba.serverSystem.item.brewing.BarrelAgingListener;
import ru.politaboba.serverSystem.item.brewing.BrewingCauldronListener;
import ru.politaboba.serverSystem.item.brewing.BrewingStorageListener;
import ru.politaboba.serverSystem.item.equipment.listener.*;
import ru.politaboba.serverSystem.item.food.listener.CampfireCookingListener;
import ru.politaboba.serverSystem.item.food.listener.FoodEatListener;
import ru.politaboba.serverSystem.item.food.FoodManager;
import ru.politaboba.serverSystem.item.equipment.EquipmentManager;

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

    private DungeonManager dungeonManager;
    private FactionDataManager factionDataManager;
    private FoodManager foodManager;
    private EquipmentManager equipmentManager;
    private ChatManager chatManager; // ИСПРАВЛЕНО: Вынесено в поле класса
    private Connection connection;
    private CaseManager caseManager;

    public FactionDataManager getFactionDataManager() {
        return factionDataManager;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public FoodManager getFoodManager() {
        return foodManager;
    }

    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    // ИСПРАВЛЕНО: Геттер чат-менеджера для команды /f chat
    public ChatManager getChatManager() {
        return chatManager;
    }

    public CaseManager getCaseManager() { return caseManager; }

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
        saveDefaultConfig();

        // 1. База данных
        initDatabase();

        getServer().getPluginManager().registerEvents(new ItemArtifactListener(), this);

        // ==================== МОДУЛЬ ДОНАТ-КЕЙСОВ ====================
        this.caseManager = new CaseManager(this);
        getServer().getPluginManager().registerEvents(new CaseListener(this.caseManager), this);

        if (getCommand("case") != null) {
            getCommand("case").setExecutor(new CaseCommand(this.caseManager));
            getCommand("case").setTabCompleter(new CaseTabCompleter());
        }

        // ==================== МОДУЛЬ КУЛИНАРИИ (ООП) ====================
        this.foodManager = new FoodManager(this);
        this.foodManager.registerAllCooking();

        // ==================== МОДУЛЬ СНАРЯЖЕНИЯ И ОРУЖИЯ (ООП) ====================
        this.equipmentManager = new EquipmentManager(this);
        this.equipmentManager.registerAllRecipes();

        // Добавляем регистрацию нашей новой команды выдачи предметов:
        if (getCommand("rpgive") != null) {
            ru.politaboba.serverSystem.item.equipment.command.EquipmentGiveCommand giveCommand =
                    new ru.politaboba.serverSystem.item.equipment.command.EquipmentGiveCommand(this);
            getCommand("rpgive").setExecutor(giveCommand);
            getCommand("rpgive").setTabCompleter(giveCommand);
        }

        // ==================== МОДУЛЬ КАСТОМНОГО ДАНЖА ====================
        ArchVindicatorBoss bossMechanics = new ArchVindicatorBoss(this);
        this.dungeonManager = new DungeonManager(this, bossMechanics);

        if (this.getCommand("generatecastle") != null) {
            this.getCommand("generatecastle").setExecutor(new TestCastleCommand(this.dungeonManager));
        }

        DungeonListener dungeonListener = new DungeonListener(this, this.dungeonManager);
        getServer().getPluginManager().registerEvents(dungeonListener, this);

        DungeonDevTool devTool = new DungeonDevTool();
        getServer().getPluginManager().registerEvents(devTool, this);

        if (this.getCommand("dungeonorigin") != null) {
            this.getCommand("dungeonorigin").setExecutor(devTool);
        }
        if (this.getCommand("dungeon") != null) {
            this.getCommand("dungeon").setExecutor(new DungeonPlayerCommand(this.dungeonManager));
        }

        // ==================== МОДУЛЬ ФРАКЦИЙ И ЭКОНОМИКИ ====================
        this.factionDataManager = new FactionDataManager(this);
        this.factionDataManager.loadAll();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (this.factionDataManager != null) {
                this.factionDataManager.saveAll();
            }
        }, 12000L, 12000L);

        // Модуль арестов (Кандалы)
        ru.politaboba.serverSystem.arrest.ArrestManager arrestManager = new ru.politaboba.serverSystem.arrest.ArrestManager(this);
        getServer().getPluginManager().registerEvents(new ru.politaboba.serverSystem.arrest.ArrestListener(arrestManager, this), this);

        if (getCommand("handcuffs") != null) {
            getCommand("handcuffs").setExecutor(new ru.politaboba.serverSystem.arrest.ArrestCommand());
        }

        // ==================== МОДУЛЬ ЧАТА И ОБЩЕНИЯ ====================
        // ИСПРАВЛЕНО: Корректная инициализация глобального менеджера чатов
        this.chatManager = new ChatManager();
        getServer().getPluginManager().registerEvents(new FactionChatListener(this, this.chatManager), this);

        GlobalChatCommand chatCommand = new GlobalChatCommand(this, this.chatManager);
        if (getCommand("g") != null) getCommand("g").setExecutor(chatCommand);
        if (getCommand("msg") != null) getCommand("msg").setExecutor(chatCommand);

        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        if (getCommand("faction") != null) {
            FactionCommand factionCommand = new FactionCommand(this);
            getCommand("faction").setExecutor(factionCommand);
            getCommand("faction").setTabCompleter(new FactionTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(new FactionListener(this), this);

        // Охота за головами
        if (getCommand("bounty") != null) {
            getCommand("bounty").setExecutor(new BountyCommand(this));
            getCommand("bounty").setTabCompleter(new BountyTabCompleter(this));
        }
        getServer().getPluginManager().registerEvents(new BountyListener(this), this);

        // Контракты
        ContractCommand contractCommand = new ContractCommand(this);
        if (getCommand("contract") != null) {
            getCommand("contract").setExecutor(contractCommand);
            getCommand("contract").setTabCompleter(new ContractTabCompleter(this));
        }
        getServer().getPluginManager().registerEvents(new ContractListener(this, contractCommand), this);

        // ==================== РЕГИСТРАЦИЯ СЛУШАТЕЛЕЙ ПРЕДМЕТОВ ====================
        getServer().getPluginManager().registerEvents(new SmokeBombListener(this), this);
        getServer().getPluginManager().registerEvents(new FoodEatListener(this), this);
        getServer().getPluginManager().registerEvents(new CampfireCookingListener(this), this);
        getServer().getPluginManager().registerEvents(new BrewingStorageListener(this), this);
        getServer().getPluginManager().registerEvents(new BrewingCauldronListener(this), this);
        getServer().getPluginManager().registerEvents(new BarrelAgingListener(this), this);

        // Слушатели модулей кастомного снаряжения
        getServer().getPluginManager().registerEvents(new EquipmentSmithingListener(this), this);
        getServer().getPluginManager().registerEvents(new MercenaryArmorListener(this), this); // ИСПРАВЛЕНО: Новый геймплейный класс
        getServer().getPluginManager().registerEvents(new WindCatcherArmorListener(this), this);
        getServer().getPluginManager().registerEvents(new ZephyrSpearListener(this), this);
        getServer().getPluginManager().registerEvents(new CastleBreakerAxeListener(this), this);
        getServer().getPluginManager().registerEvents(new MagneticWeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new StormBowListener(this), this);

        // Оставшиеся системные рецепты верстака (не являющиеся оружием/шаблонами)
        registerHandcuffsRecipe();
        registerSmokeBombRecipe();

        if (getCommand("msg") != null) {
            getCommand("msg").setTabCompleter(new MsgTabCompleter(this));
        }
        if (getCommand("w") != null) {
            getCommand("w").setTabCompleter(new MsgTabCompleter(this));
        }

        getLogger().info("=======================================");
        getLogger().info(" [ServerSystem] Все RP модули успешно запущены!");
        getLogger().info(" [ServerSystem] Режим хранения: MySQL/SQLite");
        getLogger().info("=======================================");
    }

    @Override
    public void onDisable() {
        if (this.factionDataManager != null) {
            this.factionDataManager.saveAllSynchronously();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info("[ServerSystem] Соединение с базой данных успешно закрыто.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (String factionName : factions.keySet()) {
            Team team = scoreboard.getTeam(factionName);
            if (team != null) team.unregister();

            Team enemyTeam = scoreboard.getTeam(factionName + "_enemies");
            if (enemyTeam != null) enemyTeam.unregister();
        }
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
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

    public void updateAllPlayersDisplay() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = onlinePlayer.getUniqueId();
            String factionName = playerFactionMap.get(playerUUID);

            if (factionName != null) {
                Faction faction = factions.get(factionName);
                if (faction == null) continue;

                ChatColor color = faction.getFactionColor();
                String prefixTag = factionName.substring(0, Math.min(factionName.length(), 4));

                String fullRoleString = faction.getPlayerInfoString(playerUUID);
                String overheadPrefix = ChatColor.DARK_GRAY + "[" + color + prefixTag + ChatColor.DARK_GRAY + "] "
                        + ChatColor.RESET + fullRoleString + ChatColor.RESET + " ";

                Team team = scoreboard.getTeam(onlinePlayer.getName());
                if (team == null) team = scoreboard.registerNewTeam(onlinePlayer.getName());

                team.setPrefix(overheadPrefix);
                team.addEntry(onlinePlayer.getName());

                String tabName = ChatColor.DARK_GRAY + "[" + color + prefixTag + ChatColor.DARK_GRAY + "] " + color + onlinePlayer.getName();
                onlinePlayer.setPlayerListName(tabName);

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
}