package pershkin.perspvefishing.command;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pershkin.perspvefishing.PersPvEFishing;
import pershkin.perspvefishing.config.FishingConfig;
import pershkin.perspvefishing.item.ItemManager;
import pershkin.perspvefishing.model.PlayerStats;
import pershkin.perspvefishing.model.RodType;
import pershkin.perspvefishing.model.TopEntry;
import pershkin.perspvefishing.security.CaptchaManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishCommand implements CommandExecutor, TabCompleter {

    private final PersPvEFishing plugin;

    public FishCommand(PersPvEFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("sell".equals(sub)) {
            return handleSell(sender);
        }
        if ("give".equals(sub)) {
            return handleGive(sender, args);
        }
        if ("stats".equals(sub)) {
            return handleStats(sender, args);
        }
        if ("top".equals(sub)) {
            return handleTop(sender);
        }
        if ("reload".equals(sub)) {
            return handleReload(sender);
        }
        if ("captcha".equals(sub)) {
            return handleCaptcha(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleSell(CommandSender sender) {
        FishingConfig config = plugin.getFishingConfig();
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.message("only_player", "&cThis command is only for players."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("fishing.sell")) {
            sendNoPermission(player, "fishing.sell");
            return true;
        }

        ItemManager itemManager = plugin.getItemManager();
        List<Integer> slotsToClear = new ArrayList<Integer>();
        int soldCount = 0;
        double total = 0.0D;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }

            double oneFishValue;
            ItemManager.FishData fishData = itemManager.readFishData(item);
            if (fishData != null) {
                oneFishValue = itemManager.calculateFishValue(fishData);
            } else {
                oneFishValue = itemManager.readExternalFishPrice(item, player);
                if (oneFishValue <= 0.0D) {
                    continue;
                }
            }

            int amount = Math.max(1, item.getAmount());
            total += oneFishValue * amount;
            soldCount += amount;
            slotsToClear.add(Integer.valueOf(i));
        }

        if (soldCount == 0) {
            player.sendMessage(config.message("no_custom_fish", "&eYou do not have custom fish to sell."));
            return true;
        }

        if (plugin.getEconomy() == null) {
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("error", "Vault/economy provider not found");
            String message = config.message("economy_error", "&cEconomy error: {error}");
            player.sendMessage(config.applyPlaceholders(message, placeholders));
            return true;
        }

        EconomyResponse response = plugin.getEconomy().depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("error", response.errorMessage == null ? "unknown" : response.errorMessage);
            String message = config.message("economy_error", "&cEconomy error: {error}");
            player.sendMessage(config.applyPlaceholders(message, placeholders));
            return true;
        }

        for (Integer slot : slotsToClear) {
            player.getInventory().setItem(slot.intValue(), null);
        }

        plugin.getDatabaseManager().recordSale(player.getUniqueId(), total);
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("count", String.valueOf(soldCount));
        placeholders.put("price", itemManager.trimPrice(total));
        String message = config.message("sold", "&aSold fish: {count}, earned: {price} coins.");
        player.sendMessage(config.applyPlaceholders(message, placeholders));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        FishingConfig config = plugin.getFishingConfig();
        if (!sender.hasPermission("fishing.admin")) {
            sendNoPermission(sender, "fishing.admin");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(config.message("give_usage", "&eUsage: /fish give <player> <rod_id>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(config.message("player_not_found", "&cPlayer not found or offline."));
            return true;
        }

        RodType rodType = RodType.fromId(args[2]);
        List<String> availableRods = config.getEnabledRodIds();
        if (rodType == null || !availableRods.contains(rodType.getId())) {
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("available_rods", String.join(", ", availableRods));
            String message = config.message("invalid_rod", "&cUnknown rod. Available: {available_rods}");
            sender.sendMessage(config.applyPlaceholders(message, placeholders));
            return true;
        }

        ItemStack rod = plugin.getItemManager().createRod(rodType);
        ItemStack[] leftovers = target.getInventory().addItem(rod).values().toArray(new ItemStack[0]);
        for (ItemStack leftover : leftovers) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        FishingConfig.RodSettings rodSettings = config.getRodSettings(rodType);
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("player", target.getName());
        placeholders.put("rod_id", rodType.getId());
        placeholders.put("rod_name", config.colorize("&6" + rodSettings.getDisplayName()));

        String senderMessage = config.message("given_sender", "&aYou gave {player}: {rod_id}");
        String targetMessage = config.message("given_target", "&aYou received {rod_name}");
        sender.sendMessage(config.applyPlaceholders(senderMessage, placeholders));
        target.sendMessage(config.applyPlaceholders(targetMessage, placeholders));
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        FishingConfig config = plugin.getFishingConfig();
        if (!sender.hasPermission("fishing.admin")) {
            sendNoPermission(sender, "fishing.admin");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(config.message("stats_usage", "&eUsage: /fish stats <player>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerStats stats = plugin.getDatabaseManager().getStats(target.getUniqueId());
        ItemManager itemManager = plugin.getItemManager();

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("player", target.getName() == null ? args[1] : target.getName());
        placeholders.put("total_fish", String.valueOf(stats.getTotalFish()));
        placeholders.put("biggest_fish", itemManager.formatOneDigit(stats.getBiggestFish()));
        placeholders.put("money_earned", itemManager.trimPrice(stats.getMoneyEarned()));

        sender.sendMessage(config.applyPlaceholders(config.message("stats_header", "&6Stats: &e{player}"), placeholders));
        sender.sendMessage(config.applyPlaceholders(config.message("stats_total", "&7Total fish: &f{total_fish}"), placeholders));
        sender.sendMessage(config.applyPlaceholders(config.message("stats_biggest", "&7Biggest fish: &f{biggest_fish} kg"), placeholders));
        sender.sendMessage(config.applyPlaceholders(config.message("stats_money", "&7Money earned: &f{money_earned}"), placeholders));
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        FishingConfig config = plugin.getFishingConfig();
        if (!sender.hasPermission("fishing.use")) {
            sendNoPermission(sender, "fishing.use");
            return true;
        }

        List<TopEntry> top = plugin.getDatabaseManager().getTopByMoney(config.getTopSize());
        if (top.isEmpty()) {
            sender.sendMessage(config.message("top_empty", "&eTop is empty."));
            return true;
        }

        ItemManager itemManager = plugin.getItemManager();
        sender.sendMessage(config.message("top_header", "&6Top fishers (money):"));
        int position = 1;
        for (TopEntry entry : top) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getUuid());
            String name = offlinePlayer.getName() == null ? entry.getUuid().toString().substring(0, 8) : offlinePlayer.getName();

            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("position", String.valueOf(position));
            placeholders.put("name", name);
            placeholders.put("money", itemManager.trimPrice(entry.getMoneyEarned()));
            placeholders.put("fish_count", String.valueOf(entry.getTotalFish()));
            placeholders.put("biggest_fish", itemManager.formatOneDigit(entry.getBiggestFish()));

            String line = config.message("top_line", "&e{position}. &f{name} &7- {money} coins &8({fish_count} fish)");
            sender.sendMessage(config.applyPlaceholders(line, placeholders));
            position++;
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        FishingConfig config = plugin.getFishingConfig();
        if (!sender.hasPermission("fishing.admin")) {
            sendNoPermission(sender, "fishing.admin");
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(config.message("reload_done", "&aFish config reloaded."));
        return true;
    }

    private boolean handleCaptcha(CommandSender sender, String[] args) {
        FishingConfig config = plugin.getFishingConfig();
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.message("only_player", "&cThis command is only for players."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(config.message("captcha_usage", "&eUsage: /fish captcha <code>"));
            return true;
        }

        Player player = (Player) sender;
        int result = plugin.getCaptchaManager().answer(player, args[1]);
        if (result == CaptchaManager.OK) {
            player.sendMessage(config.message("captcha_solved", "&aCaptcha passed."));
        } else if (result == CaptchaManager.WRONG) {
            player.sendMessage(config.message("captcha_wrong", "&cWrong captcha code."));
        } else {
            player.sendMessage(config.message("captcha_not_found", "&eYou do not have active captcha."));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        FishingConfig config = plugin.getFishingConfig();
        for (String line : config.getHelpMessages()) {
            sender.sendMessage(config.getMessagePrefix() + config.colorize(line));
        }
    }

    private void sendNoPermission(CommandSender sender, String permission) {
        FishingConfig config = plugin.getFishingConfig();
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("permission", permission);
        String message = config.message("no_permission", "&cNo permission: {permission}");
        sender.sendMessage(config.applyPlaceholders(message, placeholders));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        FishingConfig config = plugin.getFishingConfig();
        if (args.length == 1) {
            List<String> completions = Arrays.asList("sell", "top", "give", "stats", "reload", "captcha");
            return filterPrefix(completions, args[0]);
        }

        if (args.length == 2 && ("give".equalsIgnoreCase(args[0]) || "stats".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filterPrefix(names, args[1]);
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filterPrefix(config.getEnabledRodIds(), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
