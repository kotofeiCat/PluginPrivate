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

        // Список ключей всех НАСТОЯЩИХ рецептов верстака
        List<NamespacedKey> customRecipes = new ArrayList<>();

        // 1. Предметы, оружие и инструменты
        customRecipes.add(new NamespacedKey(plugin, "rp_smoke_bomb"));
        customRecipes.add(new NamespacedKey(plugin, "rp_halberd"));
        customRecipes.add(new NamespacedKey(plugin, "rp_handcuffs")); // Твои кандалы из ServerSystem

        // 2. Новые сырые заготовки (из обновленного FoodFactory)
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_sprinter"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_kebab"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_raw_carp"));
        customRecipes.add(new NamespacedKey(plugin, "recipe_mint_blend"));

        // Открываем актуальные рецепты для игрока при входе
        player.discoverRecipes(customRecipes);
    }
}