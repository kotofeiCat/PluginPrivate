package ru.politaboba.serverSystem.faction.listener;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.politaboba.serverSystem.ServerSystem;
import ru.politaboba.serverSystem.faction.manager.ChatManager;
import ru.politaboba.serverSystem.faction.model.Faction;

import java.util.UUID;

public class FactionChatListener implements Listener {

    private final ServerSystem plugin;
    private final ChatManager chatManager;
    private final boolean hasLuckPerms;

    public FactionChatListener(ServerSystem plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
        // Защита от крашей: проверяем, установлен ли на сервере LuckPerms
        this.hasLuckPerms = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        UUID senderUUID = sender.getUniqueId();
        String message = event.getMessage().trim();

        // Перехватываем сообщение, чтобы ванильный майнкрафт не дублировал его в стандартном формате
        event.setCancelled(true);

        String factionName = plugin.getPlayerFactionMap().get(senderUUID);
        ChatManager.ChatMode mode = chatManager.getChatMode(senderUUID);

        // 1. Быстрый глобальный чат через префикс "!"
        if (message.startsWith("!")) {
            String globalMessage = message.substring(1).trim();
            if (globalMessage.isEmpty()) return;
            sendGlobalMessage(sender, factionName, globalMessage);
            return;
        }

        // 2. Обработка фракционного радиоканала
        if (mode == ChatManager.ChatMode.FACTION) {
            if (factionName == null) {
                sender.sendMessage("§cВы не состоите во фракции! Возврат в локальный чат...");
                chatManager.setChatMode(senderUUID, ChatManager.ChatMode.LOCAL);
                sendLocalMessage(sender, factionName, message);
                return;
            }
            sendFactionMessage(sender, factionName, message);
            return;
        }

        // 3. Локальный РП-чат по умолчанию
        sendLocalMessage(sender, factionName, message);
    }

    private void sendLocalMessage(Player sender, String factionName, String message) {
        // Получаем префикс доната напрямую из LuckPerms API
        String lpPrefix = getLuckPermsPrefix(sender);

        String factionPrefix = "§8[Скиталец] ";
        if (factionName != null) {
            Faction faction = plugin.getFactions().get(factionName);
            ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GRAY;
            factionPrefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE;
        }

        // Сборка локального формата с поддержкой HEX и цветовых кодов через '&'
        String format = ChatColor.translateAlternateColorCodes('&',
                "§7[ЛОКАЛЬНЫЙ] " + lpPrefix + factionPrefix + sender.getName() + ": §7" + message);

        int radiusSquared = 50 * 50;

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getWorld().equals(sender.getWorld())) {
                if (recipient.getLocation().distanceSquared(sender.getLocation()) <= radiusSquared) {
                    recipient.sendMessage(format);
                }
            }
        }
        Bukkit.getLogger().info("[LOCAL] " + sender.getName() + ": " + message);
    }

    private void sendFactionMessage(Player sender, String factionName, String message) {
        // Получаем префикс доната напрямую из LuckPerms API
        String lpPrefix = getLuckPermsPrefix(sender);

        Faction faction = plugin.getFactions().get(factionName);
        ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GREEN;

        // Вживляем донат-префикс во фракционную рацию
        String format = ChatColor.translateAlternateColorCodes('&',
                color + "⚡ [" + factionName.toUpperCase() + "] " + lpPrefix + "§e" + sender.getName() + ": §f" + message);

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            String recipientFaction = plugin.getPlayerFactionMap().get(recipient.getUniqueId());

            if (recipientFaction != null && recipientFaction.equalsIgnoreCase(factionName.trim())) {
                recipient.sendMessage(format);
            }
        }
        Bukkit.getLogger().info("[FACTION CHAT: " + factionName + "] " + sender.getName() + ": " + message);
    }

    private void sendGlobalMessage(Player sender, String factionName, String message) {
        // Получаем префикс доната напрямую из LuckPerms API
        String lpPrefix = getLuckPermsPrefix(sender);

        String factionPrefix = "§8[Скиталец] ";
        if (factionName != null) {
            Faction faction = plugin.getFactions().get(factionName);
            ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GOLD;
            factionPrefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + color;
        }

        // Формат глобального чата со спонсорской припиской
        String format = ChatColor.translateAlternateColorCodes('&',
                "§e📢 [ГЛОБАЛ] " + lpPrefix + factionPrefix + sender.getName() + "§7: §f" + message);

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(format);
        }
    }

    /**
     * Вспомогательный метод для прямого и надёжного получения префиксов из LuckPerms API
     */
    private String getLuckPermsPrefix(Player player) {
        if (!hasLuckPerms) {
            return "";
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    // Возвращаем сырую строку префикса (цвета обработаются методом translateAlternateColorCodes)
                    return prefix + " ";
                }
            }
        } catch (Exception e) {
            // Защита на случай, если плагин запрашивает префикс во время перезагрузки LuckPerms
        }
        return "";
    }
}