package ru.politaboba.serverSystem.contract;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import ru.politaboba.serverSystem.ServerSystem;

import java.util.UUID;

public class ContractListener implements Listener {

    private final ServerSystem plugin;
    private final ContractCommand commandExecutor;

    public ContractListener(ServerSystem plugin, ContractCommand commandExecutor) {
        this.plugin = plugin;
        this.commandExecutor = commandExecutor;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Защита от утечки памяти при выходе игрока
        commandExecutor.getPendingContracts().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 1. ЛОГИКА АРХИВА
        if (event.getInventory().getHolder() instanceof ContractCommand.ContractArchiveHolder) {
            event.setCancelled(true);
            if (clickedItem.getType() == Material.BOOK && clickedItem.hasItemMeta()) {
                String name = clickedItem.getItemMeta().getDisplayName();
                if (!name.contains("#")) return;

                String[] parts = name.split("#");
                if (parts.length < 2) return;
                String id = parts[1].split(" ")[0].trim();

                for (Agreement agreement : plugin.getAgreements()) {
                    if (agreement.getId().equals(id)) {
                        player.closeInventory();
                        openAgreementAsReadObook(player, agreement);
                        break;
                    }
                }
            }
            return;
        }

        // 2. ЛОГИКА ПОДПИСАНИЯ ВХОДЯЩЕГО КОНТРАКТА
        if (event.getInventory().getHolder() instanceof ContractCommand.ContractAcceptHolder) {
            event.setCancelled(true);

            ContractCommand.ContractAcceptHolder holder = (ContractCommand.ContractAcceptHolder) event.getInventory().getHolder();
            Agreement agreement = holder.getAgreement();

            if (agreement == null) {
                player.closeInventory();
                return;
            }

            // КНОПКА "ИЗУЧИТЬ УСЛОВИЯ"
            if (clickedItem.getType() == Material.WRITABLE_BOOK) {
                player.closeInventory();
                openAgreementAsReadObook(player, agreement);

                // Отправляем интерактивную подсказку в чат для бесшовного возврата в меню
                player.sendMessage(" ");
                player.sendMessage("§7Вы открыли текст договора. Когда ознакомитесь:");
                TextComponent returnMsg = new TextComponent("§6§l👉 НАЖМИ СЮДА, ЧТОБЫ ВЕРНУТЬСЯ К ПОДПИСАНИЮ 👈");
                returnMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contract review"));
                returnMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§aВернуться в интерфейс выбора действия").create()));
                player.spigot().sendMessage(returnMsg);
                player.sendMessage(" ");
                return;
            }

            // КНОПКА "ПОДПИСАТЬ"
            if (clickedItem.getType() == Material.GREEN_CONCRETE) {
                commandExecutor.getPendingContracts().remove(player.getUniqueId());
                plugin.getAgreements().add(agreement);
                player.closeInventory();

                String fullTitle = agreement.getTitle();
                String lowerTitle = fullTitle.toLowerCase();

                if (fullTitle.contains("[ГОСУДАРСТВЕННЫЙ ПАКТ]")) {
                    if (lowerTitle.contains("мир") || lowerTitle.contains("войн") || lowerTitle.contains("капитуляц")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.5f, 0.7f);
                            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
                        }
                        Bukkit.broadcastMessage("");
                        Bukkit.broadcastMessage("§4§l⚔ [ВЕСТНИК ВОЙНЫ И МИРА] ⚔");
                        Bukkit.broadcastMessage("§cВажнейшее историческое событие между государственными лидерами!");
                        Bukkit.broadcastMessage("§e" + agreement.getPartyA() + " §7и §e" + agreement.getPartyB() + " §7подписали:");
                        Bukkit.broadcastMessage("§f§l" + fullTitle.replace("§6§l[ГОСУДАРСТВЕННЫЙ ПАКТ] §f", "").toUpperCase());
                        Bukkit.broadcastMessage("§7Текст манифеста занесен в летопись: §e/contract");
                        Bukkit.broadcastMessage("");
                    } else {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.3f);
                        }
                        Bukkit.broadcastMessage("§6📜 [Вестник Дипломатии] §l" + agreement.getPartyA() + " §7и §l" + agreement.getPartyB() + " §7заключили соглашение: §e" + fullTitle.replace("§6§l[ГОСУДАРСТВЕННЫЙ ПАКТ] §f", ""));
                    }
                } else {
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    player.sendMessage("§a§lКонтракт успешно подписан! §7Он сохранен в архив §e/contract§7.");

                    Player creator = Bukkit.getPlayer(agreement.getPartyA());
                    if (creator != null && creator.isOnline()) {
                        creator.playSound(creator.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                        creator.sendMessage("§a§l" + player.getName() + " §aподписал ваш контракт! §7Посмотреть: §e/contract§7.");
                    }

                    Bukkit.broadcastMessage("§3✉ [Сделка] Игроки §b" + agreement.getPartyA() + " §7и §b" + agreement.getPartyB() + " §7заверили частный контракт.");
                }
            }

            // КНОПКА "ОТКЛОНИТЬ"
            else if (clickedItem.getType() == Material.RED_CONCRETE) {
                commandExecutor.getPendingContracts().remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§cВы отклонили соглашение.");
            }
        }
    }

    private void openAgreementAsReadObook(Player player, Agreement agreement) {
        ItemStack writtenBook = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) writtenBook.getItemMeta();
        if (meta != null) {
            meta.setTitle(agreement.getTitle());
            meta.setAuthor(agreement.getPartyA());
            meta.setPages(agreement.getPages());
            writtenBook.setItemMeta(meta);
            player.openBook(writtenBook);
        }
    }
}