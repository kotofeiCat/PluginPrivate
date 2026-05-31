package ru.politaboba.serverSystem.Dangeon.PobornikCastle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class ItemArtifactListener implements Listener {

    private static final NamespacedKey ARTIFACT_KEY = new NamespacedKey("dungeon", "artifact_axe");

    // 1. Метод создания Топора Раскалывателя Замков
    public static ItemStack createVindicatorAxe() {
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = axe.getItemMeta();

        if (meta != null) {
            // Название: золотой текст, жирный
            meta.displayName(Component.text("Топор Раскалывателя Замков", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            // Лор предмета
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("ПКМ: Вызывает челюсти Вызывателя в направлении взгляда,", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("наносящие урон врагам.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            // Метка в PersistentDataContainer
            meta.getPersistentDataContainer().set(ARTIFACT_KEY, PersistentDataType.BOOLEAN, true);
            axe.setItemMeta(meta);
        }
        return axe;
    }

    // 2. Обработка PlayerInteractEvent
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.DIAMOND_AXE) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(ARTIFACT_KEY, PersistentDataType.BOOLEAN)) return;

        Player player = event.getPlayer();

        // Проверяем, находится ли предмет на перезарядке
        if (player.hasCooldown(Material.DIAMOND_AXE)) return;

        // Включаем кулдаун на 7 секунд (140 тиков)
        player.setCooldown(Material.DIAMOND_AXE, 140);

        // Логика спавна челюстей (Evoker Fangs)
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().setY(0).normalize(); // Движение строго горизонтально
        Location spawnBase = player.getLocation(); // Базовая локация ног для сохранения высоты пола

        for (double d = 1.5; d <= 8.0; d += 1.5) {
            Location fangLoc = spawnBase.clone().add(direction.clone().multiply(d));

            // Корректируем высоту Y под рельеф блока
            fangLoc.setY(fangLoc.getWorld().getHighestBlockYAt(fangLoc) + 1.0);

            EvokerFangs fangs = (EvokerFangs) fangLoc.getWorld().spawn(fangLoc, EvokerFangs.class, entity -> {
                entity.setOwner(player); // Устанавливаем владельца, чтобы не ранить себя
            });

            // Дополнительная безопасность от урона по самому себе на Paper API
            for (Entity nearby : fangLoc.getWorld().getNearbyEntities(fangLoc, 1.0, 2.0, 1.0)) {
                if (nearby instanceof LivingEntity livingEntity && !(nearby instanceof Player)) {
                    // Челюсти нанесут урон автоматически, так как мы выставили владельца (Owner).
                    // Здесь можно наложить дополнительный кастомный урон или эффекты при необходимости.
                }
            }
        }
    }
}