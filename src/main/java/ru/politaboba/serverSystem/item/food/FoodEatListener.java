package ru.politaboba.serverSystem.item.food;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.politaboba.serverSystem.ServerSystem;

public class FoodEatListener implements Listener {

    private final ServerSystem plugin;

    public FoodEatListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (!item.hasItemMeta()) return;

        NamespacedKey key = new NamespacedKey(plugin, "custom_dish");
        String dishType = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

        // Если это обычная ванильная еда без нашего NBT-маркера — ничего не делаем
        if (dishType == null) return;

        Player player = event.getPlayer();

        switch (dishType) {
            // ==========================================
            // КАТЕГОРИЯ: НЕУДАЧНАЯ ПОПЫТКА ГОТОВКИ (БРАК)
            // ==========================================
            case "raw_ruined":
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 0)); // Отравление на 8 секунд
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 300, 1));
                player.sendMessage("§c§oВы съели недожаренную склизкую пищу... Живот сильно скрутило.");
                break;

            case "burnt_ruined":
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0)); // Тошнота на 10 секунд
                player.sendMessage("§8§oВы попытались разжевать чистый уголь. Во рту остался мерзкий привкус гари.");
                break;

            // ==========================================
            // КАТЕГОРИЯ: ИДЕАЛЬНЫЕ РП БЛЮДА
            // ==========================================
            case "mint_tea":
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12000, 0));
                player.sendMessage("§a⚡ Вы выпили Целебный мятный чай. Силы плавно возвращаются к вам.");
                break;

            case "sprinter_steak":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9600, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 9600, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 9600, 0));
                player.sendMessage("§d⚡ Стейк «Горный Спринтер» дал вам взрывную скорость и легкость!");
                break;

            case "kebab":
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 12000, 1));
                player.sendMessage("§6⚡ Вы съели сочный Шашлык. Вы чувствуете прилив жизненных сил.");
                break;

            case "royal_carp":
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 18000, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 18000, 0));
                player.sendMessage("§b⚡ Императорский запеченный карп позволяет вам чувствовать себя в воде как дома!");
                break;

            case "dark_beer":
                // Боевые баффы (на 3 минуты = 3600 тиков)
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 3600, 1));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 3600, 0));

                // РП-эффекты жесткого опьянения
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA, 400, 0)); // Качает экран 20 секунд
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS, 200, 0)); // Темнеет в глазах 10 секунд

                player.sendMessage("§6§o*Ухх!* Напиток бьет точно в голову. Вы чувствуете дикую силу в руках, но земля уходит из-под ног!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
                break;

            case "beer_wort":
                player.sendMessage("§cВы попытались сделать глоток сырого сусла. Оно дико приторное и вяжет рот. Фу!");
                break;
        }
    }
}
