package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

public class ArchVindicatorBoss implements Listener {

    private final JavaPlugin plugin;
    public static final NamespacedKey BOSS_KEY = new NamespacedKey("dungeon", "dungeon_boss");
    private boolean phaseTwoTriggered = false;

    public ArchVindicatorBoss(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spawn(Location location) {
        Evoker boss = (Evoker) location.getWorld().spawnEntity(location, EntityType.EVOKER);

        // Починено: Использование .decorate() вместо .bold()
        boss.customName(Component.text("Верховный Инквизитор Малакай", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);

        // Починено: Изменено на Attribute.MAX_HEALTH
        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(300.0);
        }
        boss.setHealth(300.0);
        boss.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BOOLEAN, true);

        // Способность 1: Каждые 15 секунд призыв гвардейцев
        startGuardSummonTask(boss);
    }

    private void startGuardSummonTask(Evoker boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                Location spawnLoc = boss.getLocation();
                for (int i = 0; i < 2; i++) {
                    Vindicator guard = (Vindicator) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.VINDICATOR);

                    // Экипировка: Железный шлем
                    Objects.requireNonNull(guard.getEquipment()).setHelmet(new ItemStack(Material.IRON_HELMET));

                    // Эффект Скорости I
                    guard.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 0, false, true));

                    guard.customName(Component.text("Поборник-Гвардеец", NamedTextColor.DARK_GRAY));
                }
            }
        }.runTaskTimer(plugin, 300L, 300L); // 300 тиков = 15 секунд
    }

    // Отмена стандартного спавна Vex'ов
    @EventHandler
    public void onSpellCast(EntitySpellCastEvent event) {
        if (event.getEntity() instanceof Evoker evoker) {
            if (evoker.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) {
                // Проверяем имя заклинания текстом, чтобы избежать ошибок маппинга (покроет и SUMMON_VEX, и SUMMON_VEXES)
                String spellName = event.getSpell().name();
                if (spellName.equals("SUMMON_VEX") || spellName.equals("SUMMON_VEXES")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Способность 2: Фаза < 50% здоровья (Спавн Разрушителя)
    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Evoker boss)) return;
        if (!boss.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) return;

        double currentHealth = boss.getHealth() - event.getFinalDamage();

        // Починено: Изменено на Attribute.MAX_HEALTH
        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;
        double maxHealth = maxHealthAttr.getValue();

        if (currentHealth <= (maxHealth * 0.5) && !phaseTwoTriggered) {
            phaseTwoTriggered = true;

            Location loc = boss.getLocation();
            Ravager ravager = (Ravager) loc.getWorld().spawnEntity(loc, EntityType.RAVAGER);

            ravager.customName(Component.text("Осадный Зверь", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            ravager.setCustomNameVisible(true);
            ravager.setRemoveWhenFarAway(false);

            // Босс становится наездником
            ravager.addPassenger(boss);
        }
    }

    // Обработка смерти босса и выпадения лута
    @EventHandler
    public void onBossDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Evoker boss)) return;

        // Проверяем, наш ли это босс по метке в PersistentDataContainer
        if (!boss.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) return;

        // Очищаем стандартный дроп эвокера, если не хотите, чтобы с босса падали ванильные тотемы
        // event.getDrops().clear();

        // Шанс выпадения топора (0.25 = 25%). Можно изменить на любой от 0.0 до 1.0
        double dropChance = 0.25;

        java.util.Random random = new java.util.Random();
        if (random.nextDouble() <= dropChance) {
            // Создаем артефакт через статический метод, который мы написали в ItemArtifactListener
            ItemStack artifactAxe = ItemArtifactListener.createVindicatorAxe();

            // Добавляем топор в список дропа, который выпадет на землю на месте смерти
            event.getDrops().add(artifactAxe);

            // Опционально: оповещаем игроков в радиусе красивым звуком или сообщением
            boss.getLocation().getWorld().getNearbyEntities(boss.getLocation(), 15, 15, 15).forEach(entity -> {
                if (entity instanceof Player player) {
                    player.sendMessage("§6[Легенда] §eТопор Раскалывателя Замков упал с тела Инквизитора!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            });
        }
    }
}