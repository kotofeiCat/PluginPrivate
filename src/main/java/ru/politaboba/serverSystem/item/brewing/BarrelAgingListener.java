package ru.politaboba.serverSystem.item.brewing;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.List;

public class BarrelAgingListener implements Listener {

    private final ServerSystem plugin;
    private final NamespacedKey agingKey;

    public BarrelAgingListener(ServerSystem plugin) {
        this.plugin = plugin;
        this.agingKey = new NamespacedKey(plugin, "barrel_aging_start");
    }

    @EventHandler
    public void onBarrelInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BARREL) return;

        Player player = event.getPlayer();
        Barrel barrel = (Barrel) event.getClickedBlock().getState();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // 1. ЗАКЛАДКА СУСЛА В БОЧКУ
        if (hand.hasItemMeta()) {
            NamespacedKey dishKey = new NamespacedKey(plugin, "custom_dish");
            String type = hand.getItemMeta().getPersistentDataContainer().get(dishKey, PersistentDataType.STRING);

            if (type != null && type.equals("beer_wort")) {
                event.setCancelled(true); // Запрещаем открывать инвентарь бочки

                if (barrel.getPersistentDataContainer().has(agingKey, PersistentDataType.LONG)) {
                    player.sendMessage("§cВ этой бочке уже настаивается хмельной напиток!");
                    return;
                }

                // Записываем время старта
                barrel.getPersistentDataContainer().set(agingKey, PersistentDataType.LONG, System.currentTimeMillis());
                barrel.update();

                if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
                else player.getInventory().setItemInMainHand(null);

                barrel.getLocation().getWorld().playSound(barrel.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 0.8f);
                player.sendMessage("§6[Бочка] Вы залили сусло в бочку и плотно забили её шпунтом. Процесс созревания начался!");
                return;
            }
        }

        // 2. ПРОВЕРКА ГОТОВНОСТИ (Клик пустой рукой)
        if (hand.getType() == Material.AIR && barrel.getPersistentDataContainer().has(agingKey, PersistentDataType.LONG)) {
            event.setCancelled(true);

            long startTime = barrel.getPersistentDataContainer().get(agingKey, PersistentDataType.LONG);
            long diff = System.currentTimeMillis() - startTime;

            long requiredTime = 60000L; // 60 000 мс = 1 минута созревания для тестов

            if (diff < requiredTime) {
                long left = (requiredTime - diff) / 1000L;
                player.sendMessage("§e[Бочка] Пиво еще не созрело! До завершения брожения осталось: §b" + left + " сек.");
                barrel.getLocation().getWorld().playSound(barrel.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
            } else {
                // Пиво созрело! Очищаем бочку и выдаем кружку пива
                barrel.getPersistentDataContainer().remove(agingKey);
                barrel.update();

                ItemStack darkBeer = createDarkBeer();
                if (!player.getInventory().addItem(darkBeer).isEmpty()) {
                    barrel.getLocation().getWorld().dropItemNaturally(barrel.getLocation(), darkBeer);
                }

                barrel.getLocation().getWorld().playSound(barrel.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                player.sendMessage("§a§l[Бочка] Сорт созрел! Вы открыли кран бочки и налили бокал отличного Тёмного Пива!");
            }
        }
    }

    private ItemStack createDarkBeer() {
        ItemStack beer = new ItemStack(Material.POTION);
        ItemMeta meta = beer.getItemMeta();
        meta.setDisplayName("§6§lКрафтовое Тёмное Пиво");
        meta.setLore(List.of(
                "§7Выдержанное в дубовой бочке столетнее пиво.",
                "",
                "§⚡ Эффекты при употреблении:",
                " §e• Сила II (3 минуты)",
                " §e• Сопротивление I (3 минуты)",
                " §c• Эффект алкогольного опьянения"
        ));
        NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "dark_beer");
        beer.setItemMeta(meta);
        return beer;
    }
}