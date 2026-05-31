package ru.politaboba.serverSystem.faction.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Faction {
    private final String name;
    private UUID leader;
    private int diamondBank;

    // Поле для хранения цвета фракции, по умолчанию серый
    private org.bukkit.ChatColor factionColor = org.bukkit.ChatColor.GRAY;

    private final Map<String, FactionDepartment> departments = new HashMap<>();
    private final Map<UUID, PlayerRegistryData> memberRegistry = new HashMap<>();
    private final java.util.Set<UUID> blacklist = new java.util.HashSet<>();

    // Геттер и Сеттер для цвета фракции
    public org.bukkit.ChatColor getFactionColor() {
        return factionColor;
    }

    public void setFactionColor(org.bukkit.ChatColor color) {
        this.factionColor = color;
    }

    public Faction(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.diamondBank = 0;

        // Автоматически генерируем ведомства и должности
        setupDefaultState();
    }

    private void setupDefaultState() {
        // 1. Создаем базовое ведомство для обычных граждан
        FactionDepartment civil = new FactionDepartment("Жители");
        FactionRank peasant = new FactionRank("Крестьянин");
        civil.addRank(peasant);
        departments.put("жители", civil);

        // 2. АВТОМАТИЧЕСКОЕ СОЗДАНИЕ МИНИСТЕРСТВА РУКОВОДСТВА
        FactionDepartment management = new FactionDepartment("Руководство");
        FactionRank leaderRank = new FactionRank("Лидер");

        // Даем лидерскому рангу сразу все права на всякий случай
        for (FactionPermission perm : FactionPermission.values()) {
            leaderRank.addPermission(perm);
        }

        management.addRank(leaderRank);
        management.setDepartmentHead(this.leader); // Лидер является и главой этого ведомства
        departments.put("руководство", management);

        // Прописываем создателя фракции в системный реестр на должность Лидера
        assignPlayer(this.leader, "Руководство", "Лидер");
    }

    /**
     * ИСПРАВЛЕНИЕ ОШИБКИ: Метод добавления участника для FactionDataManager.
     * Если игрок загружается из БД, мы регистрируем его в реестре участников.
     * По умолчанию выдается ведомство "Жители" и должность "Крестьянин".
     * Если его должность была сохранена внутри JSON ведомств, она обновится автоматически.
     */
    public void addMember(UUID uuid) {
        if (uuid == null) return;
        // Если игрок уже есть в реестре фракции (например, лидер), не перезаписываем его данные
        if (!memberRegistry.containsKey(uuid)) {
            assignPlayer(uuid, "Жители", "Крестьянин");
        }
    }

    public Map<String, FactionDepartment> getDepartments() { return departments; }

    public void assignPlayer(UUID uuid, String deptName, String rankName) {
        // Сохраняем ключи в нижнем регистре для стабильности проверок
        memberRegistry.put(uuid, new PlayerRegistryData(deptName, rankName));
    }

    public void removePlayer(UUID uuid) {
        memberRegistry.remove(uuid);
        for (FactionDepartment dept : departments.values()) {
            if (uuid.equals(dept.getDepartmentHead())) {
                dept.setDepartmentHead(null);
            }
        }
    }

    public String getPlayerInfoString(UUID uuid) {
        if (uuid.equals(leader)) return "§6Правитель / Лидер государства";

        PlayerRegistryData data = memberRegistry.get(uuid);
        if (data == null) return "§7Ведомство: Жители | Должность: Крестьянин";

        if (uuid.equals(departments.get(data.department.toLowerCase()).getDepartmentHead())) {
            return "§3Ведомство: §b" + data.department + " §7| §6Глава Ведомства";
        }

        return "§3Ведомство: §b" + data.department + " §7| §3Должность: §e" + data.rank;
    }

    public boolean hasPermission(UUID uuid, FactionPermission perm) {
        if (uuid.equals(leader)) return true; // Лидеру законы не писаны

        PlayerRegistryData data = memberRegistry.get(uuid);
        if (data == null) return false;

        String deptName = data.department.toLowerCase();
        FactionDepartment dept = departments.get(deptName);
        if (dept == null) return false;

        if (uuid.equals(dept.getDepartmentHead())) {
            if (perm == FactionPermission.MANAGE_ROLES || perm == FactionPermission.ISSUE_PASSPORT) {
                return true;
            }
        }

        FactionRank rank = dept.getRanks().get(data.rank.toLowerCase());
        return rank != null && rank.hasPermission(perm);
    }

    public int getDiamondBank() { return diamondBank; }
    public void deposit(int amount) { this.diamondBank += amount; }
    public boolean withdraw(int amount) {
        if (this.diamondBank >= amount) {
            this.diamondBank -= amount;
            return true;
        }
        return false;
    }

    public java.util.Set<UUID> getBlacklist() { return blacklist; }
    public void addToBlacklist(UUID uuid) { blacklist.add(uuid); }
    public boolean isBlacklisted(UUID uuid) { return blacklist.contains(uuid); }

    public java.util.Set<UUID> getMembers() {
        java.util.Set<UUID> members = new java.util.HashSet<>(memberRegistry.keySet());
        members.add(leader);
        return members;
    }

    public UUID getLeader() { return leader; }
    public String getName() { return name; }
    public PlayerRegistryData getPlayerRegistryData(UUID uuid) { return memberRegistry.get(uuid); }

    public static class PlayerRegistryData {
        private final String department;
        private final String rank;

        PlayerRegistryData(String department, String rank) {
            this.department = department;
            this.rank = rank;
        }

        public String getDepartment() { return department; }
        public String getRank() { return rank; }
    }
}