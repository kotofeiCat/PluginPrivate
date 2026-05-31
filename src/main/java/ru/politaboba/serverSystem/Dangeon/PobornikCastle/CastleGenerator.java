package ru.politaboba.serverSystem.Dangeon.PobornikCastle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CastleGenerator {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public CastleGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void generateThroneRoomAsync(Location startLoc, Runnable onComplete) {
        // Запускаем расчет координат в асинхронном потоке
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BlockDataSnapshot> blocksToPlace = new ArrayList<>();
            World world = startLoc.getWorld();

            int startX = startLoc.getBlockX();
            int startY = startLoc.getBlockY();
            int startZ = startLoc.getBlockZ();

            // Размеры: 15x8x15
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 8; y++) {
                    for (int z = 0; z < 15; z++) {
                        Location currentLoc = new Location(world, startX + x, startY + y, startZ + z);
                        Material material = getMaterialForPosition(x, y, z);

                        if (material != null) {
                            blocksToPlace.add(new BlockDataSnapshot(currentLoc, material));
                        }
                    }
                }
            }

            // Передаем список на поэтапный рендеринг в основной поток
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                new SegmentedRenderer(blocksToPlace, onComplete).runTaskTimer(plugin, 1L, 1L);
            });
        });
    }

    private Material getMaterialForPosition(int x, int y, int z) {
        // 1. Пол (y = 0)
        if (y == 0) {
            return Material.POLISHED_DEEPSLATE;
        }

        // Границы стен
        boolean isWall = (x == 0 || x == 14 || z == 0 || z == 14);

        if (isWall) {
            // Потолок/Крышу оставим пустой или можно закрыть, здесь делаем коробку стен
            if (y == 7) return Material.MOSSY_COBBLESTONE;

            // 3. Окна в стенах (на высоте y=3 и y=4, через определенный шаг)
            if ((y == 3 || y == 4) && (x % 4 == 0 || z % 4 == 0)) {
                // Угловые стыки окон не делаем
                if (!((x == 0 || x == 14) && (z == 0 || z == 14))) {
                    return Material.GRAY_STAINED_GLASS_PANE;
                }
            }

            // 2. Стены — смесь замшелого булыжника и обтесанного темного дуба
            return random.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.STRIPPED_DARK_OAK_LOG;
        }

        // Внутреннее пространство зала заполняем воздухом
        return Material.AIR;
    }

    // Вспомогательный record для хранения данных блока
    private record BlockDataSnapshot(Location location, Material material) {}

    // Оптимизированный рендерер блоков порциями
    private static class SegmentedRenderer extends BukkitRunnable {
        private final List<BlockDataSnapshot> blocks;
        private final Runnable callback;
        private int currentIndex = 0;
        private static final int BATCH_SIZE = 125; // Среднее между 100 и 150

        public SegmentedRenderer(List<BlockDataSnapshot> blocks, Runnable callback) {
            this.blocks = blocks;
            this.callback = callback;
        }

        @Override
        public void run() {
            int placedInThisTick = 0;

            while (currentIndex < blocks.size() && placedInThisTick < BATCH_SIZE) {
                BlockDataSnapshot snapshot = blocks.get(currentIndex);
                Block block = snapshot.location().getBlock();

                // 4. Отключаем физику соседей (false) для оптимизации TPS
                block.setType(snapshot.material(), false);

                currentIndex++;
                placedInThisTick++;
            }

            if (currentIndex >= blocks.size()) {
                // 5. По завершении вызываем callback
                callback.run();
                cancel();
            }
        }
    }
}
