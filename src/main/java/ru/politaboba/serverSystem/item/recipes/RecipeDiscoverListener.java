package ru.politaboba.serverSystem.item.recipes;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.ArrayList;
import java.util.List;

public class RecipeDiscoverListener implements Listener {

    private final ServerSystem plugin;

    public RecipeDiscoverListener(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        List<NamespacedKey> customRecipes = new ArrayList<>();

        // 1. Старое оружие
        customRecipes.add(new NamespacedKey(plugin, "rp_smoke_bomb"));
        customRecipes.add(new NamespacedKey(plugin, "rp_halberd"));
        customRecipes.add(new NamespacedKey(plugin, "rp_handcuffs"));

        // 2. Старые заготовки
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_sprinter"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_kebab"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_carp"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_mint_blend"));

        customRecipes.add(new NamespacedKey(plugin, "rp_zephyr_spear_craft"));
        customRecipes.add(new NamespacedKey(plugin, "wind_catcher_template_craft"));
        customRecipes.add(new NamespacedKey(plugin, "artifact_axe_craft"));
        customRecipes.add(new NamespacedKey(plugin, "magnetic_greatsword_craft"));
        customRecipes.add(new NamespacedKey(plugin, "rp_storm_bow_craft"));

        // ==========================================
        // РЕГИСТРАЦИЯ ДЛЯ АВТООТКРЫТИЯ НОВЫХ РЕЦЕПТОВ
        // ==========================================
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_soldier_bread"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_ash_potato"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_cucumber_blend"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_pine_gather"));

        player.discoverRecipes(customRecipes);
    }
}