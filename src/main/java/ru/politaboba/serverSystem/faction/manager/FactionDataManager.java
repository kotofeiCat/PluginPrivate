package ru.politaboba.serverSystem.faction.manager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    private final Type departmentsType = new TypeToken<Map<String, FactionDepartment>>(){}.getType();
    private final Type registryType = new TypeToken<Map<String, Faction.PlayerRegistryData>>(){}.getType();

    public FactionDataManager(ServerSystem plugin) {
        this.plugin = plugin;
        if (plugin.getDatabaseConnection() != null) {
            createTables();
        } else {
            plugin.getLogger().severe("[Реестр] Таблицы не созданы, так как нет соединения с SQLite!");
        }
    }

    private void createTables() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        try (Statement statement = conn.createStatement()) {
            // ИСПРАВЛЕНО: LONGTEXT заменен на стандартный TEXT для SQLite
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ss_factions (" +
                    "name VARCHAR(64) PRIMARY KEY, " +
                    "leader VARCHAR(36) NOT NULL, " +
                    "diamond_bank INT DEFAULT 0, " +
                    "color VARCHAR(32) DEFAULT 'WHITE', " +
                    "blacklist TEXT, " +
                    "members TEXT, " +
                    "departments_json TEXT" +
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
                    "pages TEXT, " +
                    "timestamp BIGINT" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ss_active_dungeons (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "origin_x DOUBLE NOT NULL, " +
                    "origin_y DOUBLE NOT NULL, " +
                    "origin_z DOUBLE NOT NULL, " +
                    "state VARCHAR(32) NOT NULL" +
                    ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("[SQLite] Ошибка при создании таблиц!");
            e.printStackTrace();
        }
    }

    public void markAsModified(String factionName) {
        if (factionName != null) {
            this.modifiedFactions.add(factionName);
        }
    }

    private static class FactionSnapshot {
        final String name;
        final String leader;
        final int diamondBank;
        final String color;
        final String blacklistStr;
        final String membersJson;
        final String deptsJson;

        FactionSnapshot(String name, String leader, int diamondBank, String color, String blacklistStr, String membersJson, String deptsJson) {
            this.name = name;
            this.leader = leader;
            this.diamondBank = diamondBank;
            this.color = color;
            this.blacklistStr = blacklistStr;
            this.membersJson = membersJson;
            this.deptsJson = deptsJson;
        }
    }

    public void saveAll() {
        List<String> activeFactionNames = new ArrayList<>(plugin.getFactions().keySet());
        List<FactionSnapshot> factionsToSave = new ArrayList<>();
        List<String> factionsToClear = new ArrayList<>();

        for (String modifiedName : modifiedFactions) {
            Faction faction = plugin.getFactions().get(modifiedName);
            if (faction != null) {
                String blacklistStr = faction.getBlacklist() != null ?
                        faction.getBlacklist().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                Map<String, Faction.PlayerRegistryData> registryCopy = new HashMap<>();
                for (UUID uuid : faction.getMembers()) {
                    Faction.PlayerRegistryData data = faction.getPlayerRegistryData(uuid);
                    if (data != null) {
                        registryCopy.put(uuid.toString(), data);
                    }
                }

                String membersJson = gson.toJson(registryCopy);
                String deptsJson = gson.toJson(faction.getDepartments());

                factionsToSave.add(new FactionSnapshot(
                        faction.getName(), faction.getLeader().toString(), faction.getDiamondBank(),
                        faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE",
                        blacklistStr, membersJson, deptsJson
                ));
                factionsToClear.add(modifiedName);
            }
        }

        // Если сохранять нечего, просто чистим список измененных и выходим
        if (factionsToSave.isEmpty()) {
            factionsToClear.forEach(modifiedFactions::remove);
            return;
        }

        List<BountyOrder> bountiesSnapshot = new ArrayList<>(plugin.getBountyOrders().values());
        List<Agreement> contractsSnapshot = new ArrayList<>(plugin.getAgreements());
        Map<String, Location> dungeonsSnapshot = new HashMap<>(plugin.getDungeonManager().getActiveDungeonOrigins());

        factionsToClear.forEach(modifiedFactions::remove);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = plugin.getDatabaseConnection();
            if (conn == null) return;

            boolean originalAutoCommit = true;
            try {
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false); // Запускаем трансляцию транзакции для SQLite

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
                }

                // 2. ИСПРАВЛЕНО: Синтаксис изменен на INSERT OR REPLACE под SQLite (всего 7 параметров)
                String query = "INSERT OR REPLACE INTO ss_factions (name, leader, diamond_bank, color, blacklist, members, departments_json) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

                int savedCount = 0;
                for (FactionSnapshot snap : factionsToSave) {
                    try (PreparedStatement ps = conn.prepareStatement(query)) {
                        ps.setString(1, snap.name);
                        ps.setString(2, snap.leader);
                        ps.setInt(3, snap.diamondBank);
                        ps.setString(4, snap.color);
                        ps.setString(5, snap.blacklistStr);
                        ps.setString(6, snap.membersJson);
                        ps.setString(7, snap.deptsJson);

                        ps.executeUpdate();
                        savedCount++;
                    }
                }

                saveBountiesInternal(conn, bountiesSnapshot);
                saveContractsInternal(conn, contractsSnapshot);
                saveDungeonsInternal(conn, dungeonsSnapshot);

                conn.commit(); // Записываем всё на диск одной транзакцией

                if (savedCount > 0) {
                    plugin.getLogger().info("[Реестр-SQLite] Успешно сохранено изменений фракций: " + savedCount);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("[Реестр-SQLite] Критическая ошибка сохранения! Откат изменений.");
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
                e.printStackTrace();
            } finally {
                try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { e.printStackTrace(); }
            }
        });
    }

    public void saveAllSynchronously() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        long startTime = System.nanoTime();
        plugin.getLogger().info("[Реестр-SQLite] Пакетное сохранение перед выключением...");

        // ИСПРАВЛЕНО: Синтаксис изменен на INSERT OR REPLACE под SQLite
        String query = "INSERT OR REPLACE INTO ss_factions (name, leader, diamond_bank, color, blacklist, members, departments_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            int factionCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                for (Faction faction : plugin.getFactions().values()) {
                    String blacklistStr = faction.getBlacklist() != null ?
                            faction.getBlacklist().stream().map(UUID::toString).collect(Collectors.joining(",")) : "";

                    Map<String, Faction.PlayerRegistryData> registryCopy = new HashMap<>();
                    for (UUID uuid : faction.getMembers()) {
                        Faction.PlayerRegistryData data = faction.getPlayerRegistryData(uuid);
                        if (data != null) registryCopy.put(uuid.toString(), data);
                    }

                    ps.setString(1, faction.getName());
                    ps.setString(2, faction.getLeader().toString());
                    ps.setInt(3, faction.getDiamondBank());
                    ps.setString(4, faction.getFactionColor() != null ? faction.getFactionColor().name() : "WHITE");
                    ps.setString(5, blacklistStr);
                    ps.setString(6, gson.toJson(registryCopy));
                    ps.setString(7, gson.toJson(faction.getDepartments()));

                    ps.addBatch();
                    factionCount++;
                }
                ps.executeBatch();
            }

            saveBountiesInternal(conn, new ArrayList<>(plugin.getBountyOrders().values()));
            saveContractsInternal(conn, new ArrayList<>(plugin.getAgreements()));
            saveDungeonsInternal(conn, new HashMap<>(plugin.getDungeonManager().getActiveDungeonOrigins()));

            conn.commit();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            plugin.getLogger().info(String.format("[Реестр-SQLite] Синхронное сохранение завершено! Объединено фракций: %d (%d мс)", factionCount, durationMs));

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    private void saveBountiesInternal(Connection conn, List<BountyOrder> orders) throws SQLException {
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
        }
    }

    private void saveContractsInternal(Connection conn, List<Agreement> agreements) throws SQLException {
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
        }
    }

    private void saveDungeonsInternal(Connection conn, Map<String, Location> activeDungeons) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM ss_active_dungeons");
            String query = "INSERT INTO ss_active_dungeons (id, world, origin_x, origin_y, origin_z, state) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                for (Map.Entry<String, Location> entry : activeDungeons.entrySet()) {
                    Location loc = entry.getValue();
                    if (loc.getWorld() == null) continue;

                    ps.setString(1, entry.getKey());
                    ps.setString(2, loc.getWorld().getName());
                    ps.setDouble(3, loc.getX());
                    ps.setDouble(4, loc.getY());
                    ps.setDouble(5, loc.getZ());
                    ps.setString(6, "LOBBY");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public void loadAll() {
        Connection conn = plugin.getDatabaseConnection();
        if (conn == null) return;

        plugin.getFactions().clear();
        plugin.getPlayerFactionMap().clear();

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

                String membersStr = rs.getString("members");
                if (membersStr != null && !membersStr.isEmpty()) {
                    if (membersStr.trim().startsWith("{")) {
                        Map<String, Faction.PlayerRegistryData> registryData = gson.fromJson(membersStr, registryType);
                        if (registryData != null) {
                            registryData.forEach((uuidStr, data) -> {
                                faction.assignPlayer(UUID.fromString(uuidStr), data.getDepartment(), data.getRank());
                            });
                        }
                    } else {
                        for (String uuidStr : membersStr.split(",")) {
                            if (!uuidStr.trim().isEmpty()) faction.addMember(UUID.fromString(uuidStr.trim()));
                        }
                    }
                }

                String deptsJson = rs.getString("departments_json");
                if (deptsJson != null && !deptsJson.isEmpty()) {
                    Map<String, FactionDepartment> depts = gson.fromJson(deptsJson, departmentsType);
                    if (depts != null) faction.getDepartments().putAll(depts);
                }

                plugin.getFactions().put(name, faction);
                plugin.getPlayerFactionMap().put(leaderUUID, name);

                for (UUID memberUUID : faction.getMembers()) {
                    plugin.getPlayerFactionMap().put(memberUUID, name);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        plugin.getBountyOrders().clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ss_bounties")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("target_uuid"));
                plugin.getBountyOrders().put(uuid, new BountyOrder(rs.getString("target_name"), uuid, rs.getString("creator_name"), rs.getInt("reward")));
            }
        } catch (SQLException e) { e.printStackTrace(); }

        plugin.getAgreements().clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ss_contracts")) {
            while (rs.next()) {
                List<String> pages = gson.fromJson(rs.getString("pages"), List.class);
                plugin.getAgreements().add(new Agreement(rs.getString("id"), rs.getString("party_a"), rs.getString("party_b"), rs.getString("title"), pages, rs.getLong("timestamp")));
            }
        } catch (SQLException e) { e.printStackTrace(); }

        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ss_active_dungeons")) {
            int dungeonCount = 0;
            while (rs.next()) {
                String worldName = rs.getString("world");
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location originLoc = new Location(world, rs.getDouble("origin_x"), rs.getDouble("origin_y"), rs.getDouble("origin_z"));
                    plugin.getDungeonManager().restoreDungeonSession(rs.getString("id"), originLoc);
                    dungeonCount++;
                }
            }
            if (dungeonCount > 0) plugin.getLogger().info("[Реестр-SQLite] Успешно восстановлено активных данжей: " + dungeonCount);
        } catch (SQLException e) { e.printStackTrace(); }
    }
}