package ru.politaboba.serverSystem.contract;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import ru.politaboba.serverSystem.ServerSystem;

import java.text.SimpleDateFormat;
import java.util.*;

public class ContractCommand implements CommandExecutor {

    private final ServerSystem plugin;
    private final Map<UUID, Agreement> pendingContracts = new HashMap<>();

    public ContractCommand(ServerSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        // /contract или /contract list — открывает архив документов
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            openContractArchive(player);
            return true;
        }

        // Команда расторжения: /contract terminate <ID>
        if (args[0].equalsIgnoreCase("terminate") && args.length > 1) {
            String targetId = args[1].trim();
            Agreement agreementToTerminate = null;

            for (Agreement ag : plugin.getAgreements()) {
                if (ag.getId().equalsIgnoreCase(targetId)) {
                    agreementToTerminate = ag;
                    break;
                }
            }

            if (agreementToTerminate == null) {
                player.sendMessage("§cДоговор с ID #" + targetId + " не найден в архиве.");
                return true;
            }

            // Проверяем, является ли игрок одной из сторон (по нику или названию фракции)
            String playerFaction = plugin.getPlayerFactionMap().get(playerUUID);
            String stateName = playerFaction != null ? "Государство " + playerFaction : "";

            boolean isPartyA = agreementToTerminate.getPartyA().equals(player.getName()) || (!stateName.isEmpty() && agreementToTerminate.getPartyA().equals(stateName));
            boolean isPartyB = agreementToTerminate.getPartyB().equals(player.getName()) || (!stateName.isEmpty() && agreementToTerminate.getPartyB().equals(stateName));

            // Если это государственные фракции, расторгнуть может только лидер фракции
            if (!stateName.isEmpty()) {
                UUID leaderUUID = plugin.getFactions().get(playerFaction).getLeader();
                if ((agreementToTerminate.getPartyA().equals(stateName) || agreementToTerminate.getPartyB().equals(stateName)) && !playerUUID.equals(leaderUUID)) {
                    player.sendMessage("§cТолько Верховный Лидер государства может расторгнуть этот пакт!");
                    return true;
                }
            }

            if (!isPartyA && !isPartyB) {
                player.sendMessage("§cВы не являетесь участником этого договора, чтобы расторгать его!");
                return true;
            }

            // Удаляем контракт из архива
            plugin.getAgreements().remove(agreementToTerminate);

            // Оповещаем сервер
            String cleanTitle = agreementToTerminate.getTitle().replaceAll("§[0-9a-fk-or]", "");
            Bukkit.broadcastMessage("§c§l📜 [Дипломатия] Договор #" + agreementToTerminate.getId() + " [" + cleanTitle + "] был в одностороннем порядке расторгнут стороной §e" + player.getName() + "§c!");
            return true;
        }

        // Команда отправки: /contract send <НикПолучателя>
        if (args[0].equalsIgnoreCase("send") && args.length > 1) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() != Material.WRITABLE_BOOK) {
                player.sendMessage("§cВы должны держать в руке Книгу с пером, в которой расписаны условия!");
                return true;
            }

            BookMeta bookMeta = (BookMeta) item.getItemMeta();
            if (bookMeta == null || !bookMeta.hasPages()) {
                player.sendMessage("§cКнига пуста! Напишите условия договора.");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                player.sendMessage("§cИгрок-получатель не найден или оффлайн.");
                return true;
            }

            if (targetPlayer.getUniqueId().equals(playerUUID)) {
                player.sendMessage("§cНельзя заключить контракт с самим собой!");
                return true;
            }

            String factionA = plugin.getPlayerFactionMap().get(playerUUID);
            String factionB = plugin.getPlayerFactionMap().get(targetPlayer.getUniqueId());

            boolean isLeaderA = factionA != null && plugin.getFactions().get(factionA).getLeader().equals(playerUUID);
            boolean isLeaderB = factionB != null && plugin.getFactions().get(factionB).getLeader().equals(targetPlayer.getUniqueId());

            String partyAName;
            String partyBName;
            String prefix;

            if (isLeaderA && isLeaderB && !factionA.equals(factionB)) {
                partyAName = "Государство " + factionA;
                partyBName = "Государство " + factionB;
                prefix = "§6§l[ГОСУДАРСТВЕННЫЙ ПАКТ] §f";
            } else {
                partyAName = player.getName();
                partyBName = targetPlayer.getName();
                prefix = "§3§l[ЧАСТНАЯ СДЕЛКА] §f";
            }

            List<String> pages = new ArrayList<>(bookMeta.getPages());
            String title = pages.get(0).split("\n")[0].trim();

            if (title.length() < 3) {
                player.sendMessage("§cНа первой строчке книги должно быть написано краткое название сделки/пакта!");
                return true;
            }

            String finalTitle = prefix + title;
            String contractID = UUID.randomUUID().toString().substring(0, 6);
            Agreement agreement = new Agreement(contractID, partyAName, partyBName, finalTitle, pages, System.currentTimeMillis());
            pendingContracts.put(targetPlayer.getUniqueId(), agreement);
            player.sendMessage("§aДокумент §e#" + contractID + " §aуспешно отправлен игроку §e" + targetPlayer.getName() + "§a на подпись!");

            openAcceptMenu(targetPlayer, agreement);
            return true;
        }

        return false;
    }

    private void openContractArchive(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§0Архив Государственных Пактов");
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
        int slot = 0;

        for (Agreement target : plugin.getAgreements()) {
            if (slot >= 45) break;

            ItemStack doc = new ItemStack(Material.BOOK);
            ItemMeta meta = doc.getItemMeta();
            meta.setDisplayName("§6Документ #" + target.getId() + " §e[" + target.getTitle() + "]");

            List<String> lore = new ArrayList<>();
            lore.add("§7Между: §b" + target.getPartyA() + " §7и §b" + target.getPartyB());
            lore.add("§7Дата: §8" + sdf.format(new Date(target.getTimestamp())));
            lore.add("");
            lore.add("§cРасторгнуть в чате: §7/contract terminate " + target.getId());
            lore.add("§6§lКликните, чтобы прочитать полный текст договора");

            meta.setLore(lore);
            doc.setItemMeta(meta);
            gui.setItem(slot, doc);
            slot++;
        }
        player.openInventory(gui);
    }

    // Сделали метод PUBLIC, чтобы листенер мог повторно вернуть меню
    public void openAcceptMenu(Player leaderB, Agreement agreement) {
        Inventory gui = Bukkit.createInventory(null, 27, "§0Подписание: #" + agreement.getId());

        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§eКонтракт от §6" + agreement.getPartyA());
        List<String> lore = new ArrayList<>();
        lore.add("§7Заголовок: §b" + agreement.getTitle());
        lore.add("§6§lКликните сюда, чтобы изучить все условия договора");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        gui.setItem(13, info);

        gui.setItem(11, createCustomButton(Material.GREEN_CONCRETE, "§a§lПОДПИСАТЬ"));
        gui.setItem(15, createCustomButton(Material.RED_CONCRETE, "§c§lОТКЛОНИТЬ"));

        leaderB.openInventory(gui);
    }

    private ItemStack createCustomButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(name);
        item.setItemMeta(m);
        return item;
    }

    public Map<UUID, Agreement> getPendingContracts() { return pendingContracts; }
}