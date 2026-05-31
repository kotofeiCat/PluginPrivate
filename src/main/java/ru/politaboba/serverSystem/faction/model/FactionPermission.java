package ru.politaboba.serverSystem.faction.model;

public enum FactionPermission {
    INVITE,
    KICK,
    MANAGE_BLACK_LIST,
    WITHDRAW_MONEY,
    MANAGE_ROLES,    // Создание рангов и прав (только для Лидера и Глав)
    ISSUE_PASSPORT   // Право выдавать паспорт СЕБЕ И ДРУГИМ игрокам
}