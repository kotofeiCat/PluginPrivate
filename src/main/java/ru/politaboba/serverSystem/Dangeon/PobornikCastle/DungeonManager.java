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
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DungeonManager {

    private final JavaPlugin plugin;
    private final ArchVindicatorBoss bossMechanics;
    private final List<DungeonSession> activeSessions = new ArrayList<>();

    public DungeonManager(JavaPlugin plugin, ArchVindicatorBoss bossMechanics) {
        this.plugin = plugin;
        this.bossMechanics = bossMechanics;
    }

    public void createDungeon(Location targetLocation) {
        // Чистим и пастим блоки мироустройства
        pasteSchematic(targetLocation);

        // Создаем ОДНУ постоянную сессию управления данжем для этой локации
        DungeonSession session = new DungeonSession(plugin, targetLocation, bossMechanics, this);
        activeSessions.add(session);

        plugin.getLogger().info("[Dungeon] Сессия данжа успешно создана на координатах: " + targetLocation.toVector());
    }

    // Вынесли вставку схематики в публичный метод, чтобы DungeonSession мог вызывать его для регенерации блоков
    public void pasteSchematic(Location targetLocation) {
        String schematicName = plugin.getConfig().getString("dungeon.structure-name", "pobornik_castle");
        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName + ".schem");

        if (!schematicFile.exists()) {
            plugin.saveResource("schematics/" + schematicName + ".schem", false);
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) return;

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(targetLocation.getWorld()))) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
            }
        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Ошибка регенерации блоков WorldEdit: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public DungeonSession getNearestSession(Location loc) {
        for (DungeonSession session : activeSessions) {
            // Если у тебя один данж на весь мир, просто возвращаем его
            return session;
        }
        return null;
    }
}