package pershkin.perspvefishing.listener;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import pershkin.perspvefishing.PersPvEFishing;
import pershkin.perspvefishing.config.FishingConfig;
import pershkin.perspvefishing.item.ItemManager;
import pershkin.perspvefishing.model.FishCatch;
import pershkin.perspvefishing.model.FishRarity;
import pershkin.perspvefishing.model.LevelTier;
import pershkin.perspvefishing.model.RodType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class FishingListener implements Listener {

    private final PersPvEFishing plugin;
    private final Random random = new Random();

    public FishingListener(PersPvEFishing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemManager itemManager = plugin.getItemManager();
        FishingConfig fishingConfig = plugin.getFishingConfig();

        ItemStack rodInHand = player.getInventory().getItemInMainHand();
        RodType rodType = itemManager.getRodType(rodInHand);
        boolean isVanillaRod = fishingConfig.isVanillaRodCatchCustomFish()
                && rodType == null
                && rodInHand != null
                && rodInHand.getType() == Material.FISHING_ROD;
        if (rodType == null && !isVanillaRod) {
            return;
        }

        if (!player.hasPermission("fishing.use")) {
            return;
        }

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (rodType != null) {
                applyBiteTimeModifier(event.getHook(), fishingConfig.getRodSettings(rodType).getBiteTimeReduction());
            }
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        if (!plugin.getCaptchaManager().check(player)) {
            if (event.getCaught() instanceof Item) {
                event.getCaught().remove();
            }
            event.setExpToDrop(0);
            return;
        }

        int totalFishBefore = plugin.getDatabaseManager().getStats(player.getUniqueId()).getTotalFish();
        LevelTier levelTier = fishingConfig.isLevelsEnabled()
                ? fishingConfig.getLevelForFishCount(totalFishBefore)
                : null;
        double luckMultiplier = levelTier == null ? 1.0D : levelTier.getLuckMultiplier();
        double levelWeightBonus = levelTier == null ? 0.0D : levelTier.getWeightBonus();
        double levelPriceMultiplier = levelTier == null ? 1.0D : levelTier.getPriceMultiplier();

        FishRarity rarity = rodType == null
                ? rollVanillaRarity(fishingConfig, luckMultiplier)
                : fishingConfig.rollRarity(random, rodType, luckMultiplier);
        FishingConfig.RaritySettings raritySettings = fishingConfig.getRaritySettings(rarity);
        double rolledWeight = randomWeight(raritySettings.getWeightMin(), raritySettings.getWeightMax());
        double finalWeight;
        if (rodType == null) {
            finalWeight = roundOneDigit(rolledWeight * fishingConfig.getVanillaWeightMultiplier() + levelWeightBonus);
        } else {
            FishingConfig.RodSettings rodSettings = fishingConfig.getRodSettings(rodType);
            finalWeight = roundOneDigit(rolledWeight + rodSettings.getWeightBonus() + levelWeightBonus);
        }
        double value = roundTwoDigits(finalWeight * raritySettings.getPricePerKg() * levelPriceMultiplier);

        FishCatch fishCatch = new FishCatch(rarity, finalWeight, value);
        ItemStack fishItem = itemManager.createFishItem(fishCatch);
        if (event.getCaught() instanceof Item) {
            ((Item) event.getCaught()).setItemStack(fishItem);
        } else {
            ItemStack[] leftovers = player.getInventory().addItem(fishItem).values().toArray(new ItemStack[0]);
            for (ItemStack leftover : leftovers) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        event.setExpToDrop(fishingConfig.getCatchExp());

        plugin.getDatabaseManager().recordCatch(player.getUniqueId(), fishCatch);

        if (levelTier != null && fishingConfig.isLevelUpMessageEnabled()) {
            LevelTier newTier = fishingConfig.getLevelForFishCount(totalFishBefore + 1);
            if (newTier.getLevel() > levelTier.getLevel()) {
                Map<String, String> levelPlaceholders = new HashMap<String, String>();
                levelPlaceholders.put("level", String.valueOf(newTier.getLevel()));
                levelPlaceholders.put("max_level", String.valueOf(fishingConfig.getMaxLevel()));
                String levelMessage = fishingConfig.message("level_up", "&6Новый уровень рыбалки: &e{level}&6/&e{max_level}&6!");
                player.sendMessage(fishingConfig.applyPlaceholders(levelMessage, levelPlaceholders));
            }
        }

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("rarity", raritySettings.getColoredName());
        placeholders.put("rarity_name", raritySettings.getDisplayName());
        placeholders.put("weight", itemManager.formatOneDigit(finalWeight));
        placeholders.put("price", itemManager.trimPrice(value));
        String catchMessage = fishingConfig.message("catch", "&aYou caught: {rarity}&a ({weight} kg, {price} coins)");
        player.sendMessage(fishingConfig.applyPlaceholders(catchMessage, placeholders));
    }

    private FishRarity rollVanillaRarity(FishingConfig config, double luckMultiplier) {
        double luck = Math.max(0.0D, luckMultiplier);
        double common = config.getVanillaCommonChance();
        double rare = config.getVanillaRareChance() * luck;
        double epic = Math.max(0, 100 - config.getVanillaCommonChance() - config.getVanillaRareChance()) * luck;
        double total = common + rare + epic;
        if (total <= 0.0D) {
            return FishRarity.COMMON;
        }
        double roll = random.nextDouble() * total;
        if (roll < common) {
            return FishRarity.COMMON;
        }
        if (roll < common + rare) {
            return FishRarity.RARE;
        }
        return FishRarity.EPIC;
    }

    private void applyBiteTimeModifier(FishHook hook, double biteTimeReduction) {
        if (hook == null || biteTimeReduction <= 0.0D) {
            return;
        }

        double multiplier = 1.0D - biteTimeReduction;
        try {
            Method getMinWait = hook.getClass().getMethod("getMinWaitTime");
            Method getMaxWait = hook.getClass().getMethod("getMaxWaitTime");
            Method setMinWait = hook.getClass().getMethod("setMinWaitTime", int.class);
            Method setMaxWait = hook.getClass().getMethod("setMaxWaitTime", int.class);

            int minWait = ((Integer) getMinWait.invoke(hook)).intValue();
            int maxWait = ((Integer) getMaxWait.invoke(hook)).intValue();

            int newMin = Math.max(1, (int) Math.round(minWait * multiplier));
            int newMax = Math.max(newMin, (int) Math.round(maxWait * multiplier));

            setMinWait.invoke(hook, Integer.valueOf(newMin));
            setMaxWait.invoke(hook, Integer.valueOf(newMax));
        } catch (Exception ignored) {
        }
    }

    private double randomWeight(double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + (max - min) * random.nextDouble();
    }

    private static double roundOneDigit(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static double roundTwoDigits(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
