package ru.politaboba.serverSystem.faction.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FactionDepartment {
    private final String name;
    private UUID departmentHead; // UUID Главы этого ведомства (может быть null)
    private final Map<String, FactionRank> ranks = new HashMap<>();

    public FactionDepartment(String name) {
        this.name = name;
        this.departmentHead = null;
    }

    public String getName() { return name; }
    public Map<String, FactionRank> getRanks() { return ranks; }

    public UUID getDepartmentHead() { return departmentHead; }
    public void setDepartmentHead(UUID headUUID) { this.departmentHead = headUUID; }

    public void addRank(FactionRank rank) {
        ranks.put(rank.getName().toLowerCase(), rank);
    }

    public void removeRank(String rankName) {
        ranks.remove(rankName.toLowerCase());
    }
}