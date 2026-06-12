package ru.politaboba.serverSystem.Dangeon.PobornikCastle;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpellCastEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ArchVindicatorBoss implements Listener {

    private final JavaPlugin plugin;
    public static final NamespacedKey BOSS_KEY = new NamespacedKey("dungeon", "dungeon_boss");
    public static final NamespacedKey PHASE_TWO_KEY = new NamespacedKey("dungeon", "boss_phase_two");

    private final List<UUID> activeGuards = new ArrayList<>();
    private final int MAX_CONCURRENT_GUARDS = 4;

    // Боссбар для Малакая
    private BossBar bossBar;
    private UUID activeBossUUID = null;

    public ArchVindicatorBoss(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Инициализируем боссбар
        this.bossBar = BossBar.bossBar(
                Component.text("Верховный Инквизитор Малакай", NamedTextColor.RED, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );

        // Таска для динамического отображения боссбара игрокам рядом
        startBossBarTrackerTask();
    }

    public Evoker spawn(Location location) {
        Evoker boss = (Evoker) location.getWorld().spawnEntity(location, EntityType.EVOKER);

        boss.customName(Component.text("Верховный Инквизитор Малакай", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);

        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(400.0);
        }
        boss.setHealth(400.0);
        boss.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BOOLEAN, true);

        this.activeBossUUID = boss.getUniqueId();
        this.bossBar.progress(1.0f); // Сбрасываем полоску на 100%

        activeGuards.clear();

        startGuardSummonTask(boss);
        startFangsCircleTask(boss);

        return boss;
    }

    // Трекер боссбара: показывает полоску тем, кто рядом, и скрывает от тех, кто ушел
    private void startBossBarTrackerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeBossUUID == null) {
                    // Если босса нет, гарантированно убираем боссбар у всех на сервере
                    plugin.getServer().getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
                    return;
                }

                Entity bossEntity = plugin.getServer().getEntity(activeBossUUID);
                if (bossEntity instanceof Evoker boss && boss.isValid() && !boss.isDead()) {

                    // Обновляем проценты здоровья на полоске
                    AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                    double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 400.0;
                    float progress = (float) (boss.getHealth() / maxHealth);
                    bossBar.progress(Math.min(1.0f, Math.max(0.0f, progress)));

                    // Показываем боссбар игрокам в радиусе 30 блоков
                    List<Player> nearbyPlayers = new ArrayList<>();
                    boss.getNearbyEntities(30, 30, 30).forEach(e -> {
                        if (e instanceof Player p) nearbyPlayers.add(p);
                    });

                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (nearbyPlayers.contains(player)) {
                            player.showBossBar(bossBar);
                        } else {
                            player.hideBossBar(bossBar);
                        }
                    }
                } else {
                    // Босс умер или деспавнился
                    plugin.getServer().getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
                    activeBossUUID = null;
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // Проверка дважды в секунду (каждые 10 тиков)
    }

    private void startGuardSummonTask(Evoker boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                activeGuards.removeIf(uuid -> plugin.getServer().getEntity(uuid) == null || !Objects.requireNonNull(plugin.getServer().getEntity(uuid)).isValid());

                if (activeGuards.size() >= MAX_CONCURRENT_GUARDS) {
                    return;
                }

                Location spawnLoc = boss.getLocation();
                int toSpawn = Math.min(2, MAX_CONCURRENT_GUARDS - activeGuards.size());

                for (int i = 0; i < toSpawn; i++) {
                    Vindicator guard = (Vindicator) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.VINDICATOR);
                    Objects.requireNonNull(guard.getEquipment()).setHelmet(new ItemStack(Material.IRON_HELMET));

                    // Ошибка 3 исправлена: DAMAGE_RESISTANCE изменен на RESISTANCE
                    guard.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 0, false, true));
                    guard.customName(Component.text("Поборник-Гвардеец", NamedTextColor.DARK_GRAY));

                    boss.getNearbyEntities(15, 15, 15).stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> (Player) e)
                            .findFirst()
                            .ifPresent(guard::setTarget);

                    activeGuards.add(guard.getUniqueId());
                }

                if (toSpawn > 0) {
                    spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.8f);
                    // Ошибка 1 исправлена: EVOKE изменен на DUST_PLUME (или аналогичный доступный красивый эффект)
                    spawnLoc.getWorld().spawnParticle(Particle.DUST_PLUME, spawnLoc, 20, 0.5, 1, 0.5, 0.1);
                }
            }
        }.runTaskTimer(plugin, 300L, 300L);
    }

    private void startFangsCircleTask(Evoker boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                Location center = boss.getLocation();

                boss.getWorld().playSound(center, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 0.5f);
                // Ошибка 2 исправлена: WITCH_MAGIC заменен на TRIAL_SPAWNER_DETECTION_OMINOUS (очень красивый зловещий фиолетовый эффект колдовства)
                boss.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center.clone().add(0, 1, 0), 40, 1.0, 0.5, 1.0, 0.1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!boss.isValid() || boss.isDead()) return;

                        Location currentCenter = boss.getLocation();
                        for (int degree = 0; degree < 360; degree += 45) {
                            double radians = Math.toRadians(degree);
                            double x = Math.cos(radians) * 3.5;
                            double z = Math.sin(radians) * 3.5;

                            Location fangLoc = currentCenter.clone().add(x, 0, z);
                            fangLoc.setY(fangLoc.getWorld().getHighestBlockYAt(fangLoc) + 0.5);

                            boss.getWorld().spawn(fangLoc, EvokerFangs.class, fangs -> {
                                fangs.setOwner(boss);
                            });
                        }
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskTimer(plugin, 240L, 240L);
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

            boss.getActivePotionEffects().forEach(effect -> boss.removePotionEffect(effect.getType()));
            // Ошибка 3 исправлена: DAMAGE_RESISTANCE изменен на RESISTANCE
            boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2, false, false));
            ravager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12000, 0, false, false));

            boss.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.8f);
            // Ошибка 4 исправлена: EXPLOSION_HUGE изменен на EXPLOSION
            boss.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.2, 0.2, 0.2, 0.0);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Evoker boss)) return;
        if (!boss.getPersistentDataContainer().has(BOSS_KEY, PersistentDataType.BOOLEAN)) return;

        // Чистим боссбар при смерти босса
        plugin.getServer().getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        this.activeBossUUID = null;

        event.getDrops().clear();
        Random random = new Random();

        // 1. ГАРАНТИРОВАННЫЙ ДРОП (Книги VII уровня)
        ItemStack guaranteedBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) guaranteedBook.getItemMeta();

        if (bookMeta != null) {
            int bookRoll = random.nextInt(3);

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
            event.getDrops().add(guaranteedBook);
        }

        // 2. СИСТЕМНЫЙ ШАНСОВЫЙ ДРОП: ТОПОР РАСКАЛЫВАТЕЛЯ ЗАМКОВ (35%)
        if (random.nextDouble() <= 0.35) {
            // Проверяем, что плагин является экземпляром твоего ServerSystem, чтобы получить менеджер
            if (plugin instanceof ru.politaboba.serverSystem.ServerSystem serverSystem) {
                // Получаем менеджер и создаем ItemStack по ID "artifact_axe"
                var equipmentManager = serverSystem.getEquipmentManager(); // Убедись, что в ServerSystem есть геттер getEquipmentManager()
                if (equipmentManager != null && equipmentManager.getEquipmentById("artifact_axe") != null) {
                    ItemStack customAxe = equipmentManager.getEquipmentById("artifact_axe").createItemStack(serverSystem);
                    event.getDrops().add(customAxe);
                }
            }
        }

        // 3. СТАРЫЙ ЛЕГЕНДАРНЫЙ МЕЧ (Оставляем как альтернативный дроп с шансом 20%)
        if (random.nextDouble() <= 0.20) {
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

                org.bukkit.enchantments.Enchantment sharp = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
                org.bukkit.enchantments.Enchantment fire = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fire_aspect"));

                if (sharp != null) swordMeta.addEnchant(sharp, 7, true);
                if (fire != null) swordMeta.addEnchant(fire, 2, true);

                legendarySword.setItemMeta(swordMeta);
                event.getDrops().add(legendarySword);
            }
        }

        activeGuards.clear();

        boss.getLocation().getWorld().getNearbyEntities(boss.getLocation(), 20, 20, 20).forEach(entity -> {
            if (entity instanceof Player player) {
                player.sendMessage("§6[Данж] §eМалакай повержен! Древние артефакты выпали на арену!");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 0.8f);
            }
        });
    }
}