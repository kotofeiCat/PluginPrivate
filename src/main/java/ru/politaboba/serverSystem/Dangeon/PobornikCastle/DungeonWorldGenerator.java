package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;

public class DungeonWorldGenerator implements Listener {

    private final JavaPlugin plugin;
    private final CastleGenerator castleGenerator;
    private final ArchVindicatorBoss bossMechanics;
    private final Random random = new Random();

    // Шанс генерации структуры в новом чанке (0.05% — оптимально для редкого данжа)
    private static final double SPAWN_CHANCE = 0.0005;
    // Минимальное расстояние от спавна мира (0, 0) для защиты стартовой зоны
    private static final int MIN_DISTANCE_FROM_SPAWN = 500;

    public DungeonWorldGenerator(JavaPlugin plugin, ArchVindicatorBoss bossMechanics) {
        this.plugin = plugin;
        this.castleGenerator = new CastleGenerator(plugin);
        this.bossMechanics = bossMechanics;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Проверяем только абсолютно НАЧАЛЬНУЮ генерацию чанка (чтобы данж не спавнился повторно при обычной загрузке)
        if (!event.isNewChunk()) return;

        // Ограничиваем генерацию только обычным миром (игнорируем Незер и Энд)
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // Проверка шанса
        if (random.nextDouble() > SPAWN_CHANCE) return;

        // Выбираем центральную точку внутри чанка для анализа рельефа (от 0 до 15)
        int blockX = (chunk.getX() << 4) + 8;
        int blockZ = (chunk.getZ() << 4) + 8;

        // Проверка расстояния от нулевых координат
        if (Math.abs(blockX) < MIN_DISTANCE_FROM_SPAWN || Math.abs(blockZ) < MIN_DISTANCE_FROM_SPAWN) return;

        // Находим самую высокую точку ландшафта в этих координатах
        int highestY = world.getHighestBlockYAt(blockX, blockZ);
        Block surfaceBlock = world.getBlockAt(blockX, highestY, blockZ);

        // Алгоритм валидации поверхности
        if (!isSuitableLocation(surfaceBlock)) return;

        // Точка начала строительства (смещаем Y чуть глубже, чтобы пол сел ровно на землю)
        Location spawnLocation = new Location(world, blockX - 7, highestY - 1, blockZ - 7);

        // Отправляем логи в консоль сервера
        plugin.getLogger().info("[Dungeon] Обнаружено идеальное место! Запуск генерации Замка Поборников на координатах: "
                + spawnLocation.getBlockX() + ", " + spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ());

        // Запуск вашего плавного пакетного конструктора
        castleGenerator.generateThroneRoomAsync(spawnLocation, () -> {
            // Спавним босса в центре построенной коробки (размер 15х8х15 -> центр 7, 2, 7)
            Location bossLocation = spawnLocation.clone().add(7, 2, 7);
            bossMechanics.spawn(bossLocation);
            plugin.getLogger().info("[Dungeon] Замок Поборников успешно сгенерирован, Верховный Инквизитор Малакай призван!");
        });
    }

    /**
     * Вспомогательный метод для проверки биома и типа блока под ногами.
     * Защищает от спавна данжей посреди океана, на деревьях или в воздухе.
     */
    private boolean isSuitableLocation(Block block) {
        Material type = block.getType();
        Biome biome = block.getBiome();

        // Не строим на воде, льду, кактусах или верхушках деревьев
        if (type == Material.WATER || type == Material.ICE || type == Material.LAVA ||
                type == Material.OAK_LEAVES || type == Material.DARK_OAK_LEAVES || type == Material.AIR) {
            return false;
        }

        // Исключаем океаны и глубокие водные биомы
        if (biome == Biome.OCEAN || biome == Biome.DEEP_OCEAN || biome == Biome.WARM_OCEAN ||
                biome == Biome.LUKEWARM_OCEAN || biome == Biome.COLD_OCEAN || biome == Biome.DEEP_COLD_OCEAN) {
            return false;
        }

        // Структура идеально подходит для Темных лесов, Равнин, Тайги и Болот
        return true;
    }
}