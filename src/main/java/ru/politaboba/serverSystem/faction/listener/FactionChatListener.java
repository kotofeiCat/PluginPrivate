package ru.politaboba.serverSystem.faction.listener;

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

    public FactionChatListener(ServerSystem plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        UUID senderUUID = sender.getUniqueId();
        String message = event.getMessage().trim();

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
        String prefix = "§8[Скиталец] ";
        if (factionName != null) {
            Faction faction = plugin.getFactions().get(factionName);
            ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GRAY;
            prefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE;
        }

        String format = "§7[ЛОКАЛЬНЫЙ] " + prefix + sender.getName() + ": §7" + message;
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
        Faction faction = plugin.getFactions().get(factionName);
        ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GREEN;

        String format = color + "⚡ [" + factionName.toUpperCase() + "] §e" + sender.getName() + ": §f" + message;

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            String recipientFaction = plugin.getPlayerFactionMap().get(recipient.getUniqueId());

            // Приводим оба названия к нижнему регистру для стопроцентного совпадения
            if (recipientFaction != null && recipientFaction.toLowerCase().trim().equals(factionName.toLowerCase().trim())) {
                recipient.sendMessage(format);
            }
        }

        // Дублируем в консоль сервера, чтобы ты видел, что пакет ушел
        Bukkit.getLogger().info("[FACTION CHAT: " + factionName + "] " + sender.getName() + ": " + message);
    }

    private void sendGlobalMessage(Player sender, String factionName, String message) {
        String prefix = "§8[Скиталец] ";
        if (factionName != null) {
            Faction faction = plugin.getFactions().get(factionName);
            ChatColor color = (faction != null) ? faction.getFactionColor() : ChatColor.GOLD;
            prefix = ChatColor.DARK_GRAY + "[" + color + factionName + ChatColor.DARK_GRAY + "] " + color;
        }

        String format = "§e📢 [ГЛОБАЛ] " + prefix + sender.getName() + "§7: §f" + message;

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(format);
        }
    }
}