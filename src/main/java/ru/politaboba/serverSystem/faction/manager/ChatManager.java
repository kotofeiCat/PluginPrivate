package ru.politaboba.serverSystem.faction.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatManager {

    public enum ChatMode {
        LOCAL,   // Локальный в радиусе 50 блоков
        FACTION  // Внутрифракционный радиоканал
    }

    // Хранилище: UUID игрока -> Его текущий режим чата
    private final Map<UUID, ChatMode> playerChatModes = new HashMap<>();

    public ChatMode getChatMode(UUID uuid) {
        return playerChatModes.getOrDefault(uuid, ChatMode.LOCAL);
    }

    public void setChatMode(UUID uuid, ChatMode mode) {
        playerChatModes.put(uuid, mode);
    }

    public void toggleChatMode(UUID uuid) {
        if (getChatMode(uuid) == ChatMode.LOCAL) {
            playerChatModes.put(uuid, ChatMode.FACTION);
        } else {
            playerChatModes.put(uuid, ChatMode.LOCAL);
        }
    }
}