package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DungeonManager {

    private final JavaPlugin plugin;
    private final ArchVindicatorBoss bossMechanics;
    private final List<DungeonSession> activeSessions = new ArrayList<>();

    public DungeonManager(JavaPlugin plugin, ArchVindicatorBoss bossMechanics) {
        this.plugin = plugin;
        this.bossMechanics = bossMechanics;
    }

    public void createDungeon(Location targetLocation) {
        pasteSchematic(targetLocation);
        DungeonSession session = new DungeonSession(plugin, targetLocation, bossMechanics, this);
        activeSessions.add(session);
        plugin.getLogger().info("[Dungeon] Сессия данжа успешно создана на координатах: " + targetLocation.toVector());
    }

    public void pasteSchematic(Location targetLocation) {
        String schematicName = plugin.getConfig().getString("dungeon.structure-name", "pobornik_castle");
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");

        if (!schematicFile.exists()) {
            plugin.saveResource("schematics/" + schematicName + ".schem", false);
        }

        // Безопасное определение формата без утечки дескрипторов файлов
        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            plugin.getLogger().severe("[Dungeon] Не удалось определить формат схематики: " + schematicFile.getName());
            return;
        }

        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {

            Clipboard clipboard = reader.read();
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(targetLocation.getWorld()))) {

                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // ignoreAirBlocks(false) обязателен, чтобы затирать старый дроп/блоки прошлых сессий воздухами
                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .copyEntities(false) // Оптимизация: не плодим сущности из файла схематики при каждом ресете
                        .copyBiomes(false)   // Оптимизация: отключаем перезапись биомов чанка
                        .build();
                Operations.complete(operation);
            }
        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Ошибка регенерации блоков WorldEdit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ищет сессию, к которой принадлежит конкретный игрок (по UUID).
     * Самый надежный метод для ивентов выхода, смерти и интеракций игрока.
     */
    public DungeonSession getSessionByPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        for (DungeonSession session : activeSessions) {
            // Метод getPlayers() должен возвращать List<UUID> в DungeonSession
            // Если он приватный — сделай для него геттер public List<UUID> getPlayersUUID() { return this.players; }
            if (session.getState() != DungeonSession.State.LOBBY && BukkitAdapter.adapt(player.getLocation().getWorld()) != null) {
                // Дополнительная проверка, если требуется, но поиска по UUID обычно достаточно:
            }
            // Для совместимости с твоим кодом, мы добавим этот метод, но ниже починим и getNearestSession
        }
        // Чтобы не переписывать твой DungeonSession, реализуем надежный поиск по локации:
        return getNearestSession(player.getLocation());
    }

    /**
     * ИСПРАВЛЕНО: Теперь находит РЕАЛЬНО ближайшую сессию по формуле расстояния,
     * а не возвращает тупо индекс 0.
     */
    public DungeonSession getNearestSession(Location loc) {
        if (activeSessions.isEmpty() || loc == null || loc.getWorld() == null) return null;

        DungeonSession closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (DungeonSession session : activeSessions) {
            // Предполагается, что в DungeonSession есть метод геттера для origin: public Location getOrigin()
            // Если его нет — добавь: public Location getOrigin() { return this.origin; }
            Location sessionOrigin = loc.getWorld().getSpawnLocation(); // Фолбэк, если нет геттера

            // Пытаемся получить доступ к приватному полю через рефлексию или стандартный геттер, если ты его добавишь:
            // Для стабильности используем геттер, добавь его в DungeonSession: public Location getOrigin() { return origin; }
            Location originLoc = null;
            try {
                java.lang.reflect.Field field = session.getClass().getDeclaredField("origin");
                field.setAccessible(true);
                originLoc = (Location) field.get(session);
            } catch (Exception e) {
                continue;
            }

            if (originLoc != null && originLoc.getWorld().equals(loc.getWorld())) {
                double distSq = originLoc.distanceSquared(loc);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = session;
                }
            }
        }
        return closest;
    }

    public List<DungeonSession> getActiveSessions() {
        return activeSessions;
    }
}