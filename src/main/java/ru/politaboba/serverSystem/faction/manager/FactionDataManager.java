package ru.politaboba.serverSystem.faction.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.bounty.BountyOrder;
import ru.politaboba.serverSystem.contract.Agreement;
import ru.politaboba.serverSystem.faction.model.Faction;
import ru.politaboba.serverSystem.faction.model.FactionDepartment;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FactionDataManager {

    private final ServerSystem plugin;
    private final Gson gson = new Gson();
    private final Set<String> modifiedFactions = ConcurrentHashMap.newKeySet();

    // Точный тип данных для корректной десериализации сложных карт через Gson
    private final Type departmentsType = new TypeToken<Map<String, FactionDepartment>>(){}.getType();

    public FactionDataManager(ServerSystem plugin) {
        this.plugin = plugin;
        if (plugin.getDatabaseConnection() != null) {
            createTables();
        } else {
            plugin.getLogger().severe("[Реестр] Таблицы не созданы, так как нет соединения с MySQL!");
        }
    }

    private void createTables() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        try (Statement statement = conn.createStatement()) {
            // Добавлено поле members для сохранения обычных участников фракции
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ss_factions (" +
                    "name VARCHAR(64) PRIMARY KEY, " +
                    "leader VARCHAR(36) NOT NULL, " +
                    "diamond_bank INT DEFAULT 0, " +
                    "color VARCHAR(32) DEFAULT 'WHITE', " +
                    "blacklist TEXT, " +
                    "members TEXT, " +
                    "departments_json LONGTEXT" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ss_bounties (" +
                    "target_uuid VARCHAR(36) PRIMARY KEY, " +
                    "target_name VARCHAR(16), " +
                    "creator_name VARCHAR(16), " +
                    "reward INT" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ss_contracts (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "party_a VARCHAR(64), " +
                    "party_b VARCHAR(64), " +
                    "title TEXT, " +
                    "pages LONGTEXT, " +
                    "timestamp BIGINT" +
                    ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("[MySQL] Ошибка при создании таблиц!");
            e.printStackTrace();
        }
    }

    public void markAsModified(String factionName) {
        if (factionName != null) {
            this.modifiedFactions.add(factionName);
        }
    }

    // ==========================================
    // ПОЛНОСТЬЮ АСИНХРОННОЕ СОХРАНЕНИЕ В MYSQL
    // ==========================================
    public void saveAll() {
        List<String> activeFactionNames = new ArrayList<>(plugin.getFactions().keySet());
        List<Faction> factionsToSave = new ArrayList<>();
        List<String> factionsToClear = new ArrayList<>();

        for (String modifiedName : modifiedFactions) {
            Faction faction = plugin.getFactions().get(modifiedName);
            if (faction != null) {
                factionsToSave.add(faction);
                factionsToClear.add(modifiedName);
            }
        }

        List<BountyOrder> bountiesSnapshot = new ArrayList<>(plugin.getBountyOrders().values());
        List<Agreement> contractsSnapshot = new ArrayList<>(plugin.getAgreements());

        factionsToClear.forEach(modifiedFactions::remove);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = plugin.getDatabaseConnection();
            if (conn == null) return;

            // 1. Очистка удаленных фракций
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM ss_factions")) {
                while (rs.next()) {
                    String dbFactionName = rs.getString("name");
                    if (!activeFactionNames.contains(dbFactionName)) {
                        try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM ss_factions WHERE name = ?")) {
                            delPs.setString(1, dbFactionName);
                            delPs.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // 2. Сохранение измененных фракций (Добавлен параметр members)
            String query = "INSERT INTO ss_factions (name, leader, diamond_bank, color, blacklist, members, departments_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "leader = ?, diamond_bank = ?, color = ?, blacklist = ?, members = ?, departments_json = ?";

            int savedCount = 0;
            for (Faction faction : factionsToSave) {
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    String blacklistStr = faction.getBlacklist() != null ?
                            faction.getBlacklist().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                    String membersStr = faction.getMembers() != null ?
                            faction.getMembers().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                    String deptsJson = gson.toJson(faction.getDepartments());

                    // Параметры для INSERT
                    ps.setString(1, faction.getName());
                    ps.setString(2, faction.getLeader().toString());
                    ps.setInt(3, faction.getDiamondBank());
                    ps.setString(4, faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE");
                    ps.setString(5, blacklistStr);
                    ps.setString(6, membersStr);
                    ps.setString(7, deptsJson);

                    // Параметры для UPDATE
                    ps.setString(8, faction.getLeader().toString());
                    ps.setInt(9, faction.getDiamondBank());
                    ps.setString(10, faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE");
                    ps.setString(11, blacklistStr);
                    ps.setString(12, membersStr);
                    ps.setString(13, deptsJson);

                    ps.executeUpdate();
                    savedCount++;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            // 3. Сохранение систем розыска и контрактов
            saveBountiesInternal(conn, bountiesSnapshot);
            saveContractsInternal(conn, contractsSnapshot);

            if (savedCount > 0) {
                plugin.getLogger().info("[Реестр-MySQL] Успешно обновлено фракций в БД: " + savedCount);
            }
        });
    }

    // ==========================================
    // СИНХРОННОЕ ОПТИМИЗИРОВАННОЕ СОХРАНЕНИЕ (BATCH)
    // ==========================================
    public void saveAllSynchronously() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        long startTime = System.nanoTime();
        plugin.getLogger().info("[Реестр-MySQL] Принудительное пакетное сохранение в БД перед выключением...");

        String query = "INSERT INTO ss_factions (name, leader, diamond_bank, color, blacklist, members, departments_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "leader = ?, diamond_bank = ?, color = ?, blacklist = ?, members = ?, departments_json = ?";

        int factionCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            for (Faction faction : plugin.getFactions().values()) {
                String blacklistStr = faction.getBlacklist() != null ?
                        faction.getBlacklist().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                String membersStr = faction.getMembers() != null ?
                        faction.getMembers().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                String deptsJson = gson.toJson(faction.getDepartments());

                ps.setString(1, faction.getName());
                ps.setString(2, faction.getLeader().toString());
                ps.setInt(3, faction.getDiamondBank());
                ps.setString(4, faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE");
                ps.setString(5, blacklistStr);
                ps.setString(6, membersStr);
                ps.setString(7, deptsJson);

                ps.setString(8, faction.getLeader().toString());
                ps.setInt(9, faction.getDiamondBank());
                ps.setString(10, faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE");
                ps.setString(11, blacklistStr);
                ps.setString(12, membersStr);
                ps.setString(13, deptsJson);

                ps.addBatch();
                factionCount++;
            }
            ps.executeBatch(); // Выполняем одним сетевым пакетом
        } catch (SQLException e) {
            e.printStackTrace();
        }

        saveBountiesInternal(conn, new ArrayList<>(plugin.getBountyOrders().values()));
        saveContractsInternal(conn, new ArrayList<>(plugin.getAgreements()));

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        plugin.getLogger().info(String.format("[Реестр-MySQL] Пакетное сохранение завершено! Фракций: %d. Время: %d мс", factionCount, durationMs));
    }

    private void saveBountiesInternal(Connection conn, List<BountyOrder> orders) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM ss_bounties");
            String query = "INSERT INTO ss_bounties (target_uuid, target_name, creator_name, reward) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                for (BountyOrder order : orders) {
                    ps.setString(1, order.getTargetUUID().toString());
                    ps.setString(2, order.getTargetName());
                    ps.setString(3, order.getCreatorName());
                    ps.setInt(4, order.getRewardAmount());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveContractsInternal(Connection conn, List<Agreement> agreements) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM ss_contracts");
            String query = "INSERT INTO ss_contracts (id, party_a, party_b, title, pages, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                for (Agreement ag : agreements) {
                    ps.setString(1, ag.getId());
                    ps.setString(2, ag.getPartyA());
                    ps.setString(3, ag.getPartyB());
                    ps.setString(4, ag.getTitle());
                    ps.setString(5, gson.toJson(ag.getPages()));
                    ps.setLong(6, ag.getTimestamp());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // СИНХРОННАЯ ЗАГРУЗКА ИЗ MYSQL ПРИ СТАРТЕ
    // ==========================================
    public void loadAll() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        plugin.getFactions().clear();
        plugin.getPlayerFactionMap().clear();

        // 1. Загрузка Фракций
        String query = "SELECT * FROM ss_factions";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                String name = rs.getString("name");
                UUID leaderUUID = UUID.fromString(rs.getString("leader"));

                Faction faction = new Faction(name, leaderUUID);
                faction.deposit(rs.getInt("diamond_bank") - faction.getDiamondBank());

                String colorStr = rs.getString("color");
                if (colorStr != null) {
                    try { faction.setFactionColor(org.bukkit.ChatColor.valueOf(colorStr)); } catch (IllegalArgumentException ignored) {}
                }

                String blacklistStr = rs.getString("blacklist");
                if (blacklistStr != null && !blacklistStr.isEmpty()) {
                    for (String uuidStr : blacklistStr.split(",")) {
                        faction.addToBlacklist(UUID.fromString(uuidStr));
                    }
                }

                // Исправлено: Восстановление мемберов фракции из БД
                String membersStr = rs.getString("members");
                if (membersStr != null && !membersStr.isEmpty()) {
                    for (String uuidStr : membersStr.split(",")) {
                        UUID memberUUID = UUID.fromString(uuidStr);
                        faction.addMember(memberUUID); // Убедись, что этот метод есть в классе Faction
                    }
                }

                // Исправлено: Карта департаментов теперь десериализуется строго в объекты типов FactionDepartment
                String deptsJson = rs.getString("departments_json");
                if (deptsJson != null && !deptsJson.isEmpty()) {
                    Map<String, FactionDepartment> depts = gson.fromJson(deptsJson, departmentsType);
                    if (depts != null) {
                        faction.getDepartments().putAll(depts);
                    }
                }

                // Заполнение глобальных кэш-мап плагина для быстрого доступа к данным
                plugin.getFactions().put(name, faction);
                plugin.getPlayerFactionMap().put(leaderUUID, name);

                if (faction.getMembers() != null) {
                    for (UUID memberUUID : faction.getMembers()) {
                        plugin.getPlayerFactionMap().put(memberUUID, name);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 2. Загрузка Bounties
        plugin.getBountyOrders().clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ss_bounties")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("target_uuid"));
                plugin.getBountyOrders().put(uuid, new BountyOrder(
                        rs.getString("target_name"), uuid,
                        rs.getString("creator_name"), rs.getInt("reward")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // 3. Загрузка Contracts
        plugin.getAgreements().clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ss_contracts")) {
            while (rs.next()) {
                List<String> pages = gson.fromJson(rs.getString("pages"), List.class);
                plugin.getAgreements().add(new Agreement(
                        rs.getString("id"), rs.getString("party_a"),
                        rs.getString("party_b"), rs.getString("title"),
                        pages, rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}