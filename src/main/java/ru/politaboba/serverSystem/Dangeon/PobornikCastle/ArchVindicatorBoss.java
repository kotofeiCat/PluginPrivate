package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class ArchVindicatorBoss implements Listener {

    private final JavaPlugin plugin;
    public static final NamespacedKey BOSS_KEY = new NamespacedKey("dungeon", "dungeon_boss");
    public static final NamespacedKey PHASE_TWO_KEY = new NamespacedKey("dungeon", "boss_phase_two");

    public ArchVindicatorBoss(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Evoker spawn(Location location) {
        Evoker boss = (Evoker) location.getWorld().spawnEntity(location, EntityType.EVOKER);

        boss.customName(Component.text("Верховный Инквизитор Малакай", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);

        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(400.0); // Увеличили здоровье до 400 для большей живучести
        }
        boss.setHealth(400.0);
        boss.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BOOLEAN, true);

        // Таймеры способностей
        startGuardSummonTask(boss);
        startFangsCircleTask(boss); // Новая интересная ульта

        return boss;
    }

    // Способность 1: Призыв охраны (Раз в 15 сек)
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
                    Objects.requireNonNull(guard.getEquipment()).setHelmet(new ItemStack(Material.IRON_HELMET));
                    guard.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 0, false, true));
                    guard.customName(Component.text("Поборник-Гвардеец", NamedTextColor.DARK_GRAY));
                }
            }
        }.runTaskTimer(plugin, 300L, 300L);
    }

    // Способность 3 (УЛЬТА): Круг смерти (Раз в 10 секунд бьет челюстями по кругу)
    private void startFangsCircleTask(Evoker boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                Location center = boss.getLocation();
                boss.getWorld().playSound(center, org.bukkit.Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 0.5f);

                // Спавним 8 челюстей по кругу радиусом 3 блока вокруг босса
                for (int degree = 0; degree < 360; degree += 45) {
                    double radians = Math.toRadians(degree);
                    double x = Math.cos(radians) * 3.0;
                    double z = Math.sin(radians) * 3.0;

                    Location fangLoc = center.clone().add(x, 0, z);
                    fangLoc.setY(fangLoc.getWorld().getHighestBlockYAt(fangLoc) + 1.0);

                    boss.getWorld().spawn(fangLoc, EvokerFangs.class, fangs -> {
                        fangs.setOwner(boss);
                    });
                }
            }
        }.runTaskTimer(plugin, 200L, 200L); // 200 тиков = 10 секунд
    }

    @EventHandler
    public void onSpellCast(EntitySpellCastEvent event) {
        if (event.getEntity() instanceof Evoker evoker) {
            if (evoker.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) {
                String spellName = event.getSpell().name();
                if (spellName.equals("SUMMON_VEX") || spellName.equals("SUMMON_VEXES")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Способность 2: Фаза 50% здоровья (Осадный зверь)
    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Evoker boss)) return;
        if (!boss.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) return;
        if (boss.getPersistentDataContainer().has(PHASE_TWO_KEY, PersistentDataType.BOOLEAN)) return;

        double currentHealth = boss.getHealth() - event.getFinalDamage();
        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) return;
        double maxHealth = maxHealthAttr.getValue();

        if (currentHealth <= (maxHealth * 0.5)) {
            boss.getPersistentDataContainer().set(PHASE_TWO_KEY, PersistentDataType.BOOLEAN, true);

            Location loc = boss.getLocation();
            Ravager ravager = (Ravager) loc.getWorld().spawnEntity(loc, EntityType.RAVAGER);

            ravager.customName(Component.text("Осадный Зверь Малакая", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            ravager.setCustomNameVisible(true);
            ravager.setRemoveWhenFarAway(false);

            ravager.addPassenger(boss);
            boss.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.8f);
        }
    }

    // Обработка смерти и кастомного крутого дропа
    @EventHandler
    public void onBossDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Evoker boss)) return;
        if (!boss.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) return;

        // Очищаем ванильный дроп (чтобы не падали обычные изумруды и тотемы, если это нужно)
        event.getDrops().clear();

        Random random = new Random();

        // ----------------------------------------------------------------
        // 1. ГАРАНТИРОВАННЫЙ ДРОП (100%): Книга 7 уровня (Защита, Острота или Сила)
        // ----------------------------------------------------------------
        ItemStack guaranteedBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) guaranteedBook.getItemMeta();

        if (bookMeta != null) {
            int bookRoll = random.nextInt(3); // 0, 1, 2

            if (bookRoll == 0) {
                org.bukkit.enchantments.Enchantment prot = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
                if (prot != null) bookMeta.addStoredEnchant(prot, 7, true);
                bookMeta.displayName(Component.text("Реликвия Инквизиции: Защита VII", NamedTextColor.AQUA, TextDecoration.BOLD));
            } else if (bookRoll == 1) {
                org.bukkit.enchantments.Enchantment sharp = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
                if (sharp != null) bookMeta.addStoredEnchant(sharp, 7, true);
                bookMeta.displayName(Component.text("Реликвия Инквизиции: Острота VII", NamedTextColor.RED, TextDecoration.BOLD));
            } else {
                org.bukkit.enchantments.Enchantment power = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("power"));
                if (power != null) bookMeta.addStoredEnchant(power, 7, true);
                bookMeta.displayName(Component.text("Реликвия Инквизиции: Сила VII", NamedTextColor.GREEN, TextDecoration.BOLD));
            }
            guaranteedBook.setItemMeta(bookMeta);
            event.getDrops().add(guaranteedBook); // Добавляем книгу в дроп
        }

        // ----------------------------------------------------------------
        // 2. ШАНСОВЫЙ ДРОП (25%): Кастомный крутой меч «Каратель Малакая»
        // ----------------------------------------------------------------
        if (random.nextDouble() <= 0.25) {
            ItemStack legendarySword = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta swordMeta = legendarySword.getItemMeta();

            if (swordMeta != null) {
                swordMeta.displayName(Component.text("⚔️ Каратель Инквизитора Малакая", NamedTextColor.GOLD, TextDecoration.BOLD));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Древнее оружие Верховного Инквизитора,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("выкованное в пламени подземных тюрем.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(""));
                lore.add(Component.text("Класс предмета: Легендарный артефакт", NamedTextColor.DARK_PURPLE));
                swordMeta.lore(lore);

                // Зачаровываем меч на Остроту 7 и Заговор огня 2 в обход ограничений
                org.bukkit.enchantments.Enchantment sharp = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
                org.bukkit.enchantments.Enchantment fire = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fire_aspect"));

                if (sharp != null) swordMeta.addEnchant(sharp, 7, true);
                if (fire != null) swordMeta.addEnchant(fire, 2, true);

                legendarySword.setItemMeta(swordMeta);
                event.getDrops().add(legendarySword); // Добавляем меч в дроп
            }
        }

        // Красивое оповещение игроков
        boss.getLocation().getWorld().getNearbyEntities(boss.getLocation(), 20, 20, 20).forEach(entity -> {
            if (entity instanceof Player player) {
                player.sendMessage("§6[Данж] §eМалакай повержен! Древние артефакты выпали на арену!");
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.8f);
            }
        });
    }
}