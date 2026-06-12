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
import java.util.*;

public class DungeonManager {

    private final JavaPlugin plugin;
    private final ArchVindicatorBoss bossMechanics;

    // Новые структуры данных для синхронизации с базой данных и оптимизации
    private final Map<String, Location> activeDungeonOrigins = new HashMap<>();
    private final Map<Location, DungeonSession> activeSessions = new HashMap<>();

    public DungeonManager(JavaPlugin plugin, ArchVindicatorBoss bossMechanics) {
        this.plugin = plugin;
        this.bossMechanics = bossMechanics;
    }

    /**
     * Возвращает карту всех зарегистрированных точек данжей (ID -> Location)
     * Используется напрямую в FactionDataManager для сохранения в MySQL/SQLite!
     */
    public Map<String, Location> getActiveDungeonOrigins() {
        return this.activeDungeonOrigins;
    }

    /**
     * Создание нового данжа в мире (например, командой админа)
     */
    public void createDungeon(String id, Location targetLocation) {
        // Сохраняем точку в реестр данжей
        this.activeDungeonOrigins.put(id, targetLocation);

        // Регенерируем блоки схематики
        pasteSchematic(targetLocation);

        // Создаем рабочую сессию
        DungeonSession session = new DungeonSession(plugin, targetLocation, bossMechanics, this);
        this.activeSessions.put(targetLocation, session);

        plugin.getLogger().info("[Dungeon] Создан новый данж '" + id + "' на координатах: " + targetLocation.toVector());
    }

    /**
     * Метод восстановления данжей из базы данных при старте сервера
     * Вызывается автоматически из FactionDataManager.loadAll()
     */
    public void restoreDungeonSession(String id, Location targetLocation) {
        // Восстанавливаем ID и локацию в памяти плагина
        this.activeDungeonOrigins.put(id, targetLocation);

        // Создаем чистую сессию в режиме ожидания игроков (LOBBY)
        DungeonSession restoredSession = new DungeonSession(plugin, targetLocation, bossMechanics, this);
        this.activeSessions.put(targetLocation, restoredSession);

        // Обновляем блоки данжа, чтобы стереть возможные следы прошлого прерванного сеанса
        pasteSchematic(targetLocation);

        plugin.getLogger().info("[Dungeon] Успешно восстановлен данж '" + id + "' после рестарта.");
    }

    public void pasteSchematic(Location targetLocation) {
        String schematicName = plugin.getConfig().getString("dungeon.structure-name", "pobornik_castle");
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");

        if (!schematicFile.exists()) {
            plugin.saveResource("schematics/" + schematicName + ".schem", false);
        }

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

                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .copyEntities(false)
                        .copyBiomes(false)
                        .build();
                Operations.complete(operation);
            }
        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Ошибка регенерации блоков WorldEdit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ищет сессию, к которой принадлежит конкретный игрок.
     */
    public DungeonSession getSessionByPlayer(Player player) {
        // Сначала ищем по прямому вхождению UUID в сессиях
        for (DungeonSession session : activeSessions.values()) {
            // Если в твоем DungeonSession список игроков называется 'players' (List<UUID>):
            // Проверяем, находится ли игрок внутри этой запущенной сессии
            try {
                java.lang.reflect.Field field = session.getClass().getDeclaredField("players");
                field.setAccessible(true);
                List<UUID> sessionPlayers = (List<UUID>) field.get(session);
                if (sessionPlayers != null && sessionPlayers.contains(player.getUniqueId())) {
                    return session;
                }
            } catch (Exception ignored) {}
        }

        // Фолбэк: если игрок еще не зашел в пати, но кликает по элементам структуры — ищем ближайшую сессию
        return getNearestSession(player.getLocation());
    }

    /**
     * Находит ближайшую сессию на основе сохраненной карты активных точек.
     * Работает без рефлексии по прямому итерированию локаций-ключей.
     */
    public DungeonSession getNearestSession(Location loc) {
        if (activeSessions.isEmpty() || loc == null || loc.getWorld() == null) return null;

        DungeonSession closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Map.Entry<Location, DungeonSession> entry : activeSessions.entrySet()) {
            Location originLoc = entry.getKey();

            if (originLoc.getWorld().equals(loc.getWorld())) {
                double distSq = originLoc.distanceSquared(loc);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = entry.getValue();
                }
            }
        }
        return closest;
    }

    /**
     * Возвращает коллекцию всех запущенных на сервере сессий данжей
     */
    public Collection<DungeonSession> getActiveSessions() {
        return activeSessions.values();
    }
}