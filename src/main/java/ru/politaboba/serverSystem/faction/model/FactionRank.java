package ru.politaboba.serverSystem.faction.model;

import java.util.HashSet;
import java.util.Set;

public class FactionRank {
    private final String name;
    private final Set<FactionPermission> permissions;

    public FactionRank(String name) {
        this.name = name;
        this.permissions = new HashSet<>();
    }

    public String getName() { return name; }
    public Set<FactionPermission> getPermissions() { return permissions; }

    public void addPermission(FactionPermission perm) { permissions.add(perm); }
    public void removePermission(FactionPermission perm) { permissions.remove(perm); }
    public boolean hasPermission(FactionPermission perm) { return permissions.contains(perm); }
}