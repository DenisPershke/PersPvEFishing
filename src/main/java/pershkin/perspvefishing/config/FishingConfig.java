package pershkin.perspvefishing.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pershkin.perspvefishing.model.FishRarity;
import pershkin.perspvefishing.model.RodType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class FishingConfig {

    private final EnumMap<FishRarity, RaritySettings> raritySettings = new EnumMap<FishRarity, RaritySettings>(FishRarity.class);
    private final EnumMap<RodType, RodSettings> rodSettings = new EnumMap<RodType, RodSettings>(RodType.class);
    private final HashMap<String, String> messages = new HashMap<String, String>();
    private final ArrayList<String> helpMessages = new ArrayList<String>();
    private final ArrayList<String> rodLoreTemplate = new ArrayList<String>();
    private final ArrayList<String> fishLoreTemplate = new ArrayList<String>();
    private String fishNameTemplate = "{rarity} fish";
    private String messagePrefix = "";
    private int topSize = 10;
    private boolean customFishRequireCustomModelData = true;
    private String customFishPdcNamespace = "";
    private String customFishPdcKey = "";
    private String customFishApiClass = "";
    private String customFishApiMethod = "";

    public FishingConfig(FileConfiguration config) {
        reload(config);
    }

    public void reload(FileConfiguration config) {
        loadRarities(config);
        loadRods(config);
        loadFormats(config);
        loadMessages(config);
        loadCustomFish(config);
        topSize = Math.max(1, config.getInt("top.size", 10));
        messagePrefix = colorize(config.getString("messages.prefix", "&6[NAMC-Fish]&r "));
    }

    public FishRarity rollRarity(Random random, RodType rodType) {
        RodSettings rod = getRodSettings(rodType);
        Map<FishRarity, Integer> chances = rod.getChancesOrNull();
        if (chances == null || chances.isEmpty()) {
            chances = getGlobalChances();
        }
        return rollByChances(random, chances);
    }

    public RaritySettings getRaritySettings(FishRarity rarity) {
        RaritySettings settings = raritySettings.get(rarity);
        if (settings == null) {
            return RaritySettings.defaultOf(rarity);
        }
        return settings;
    }

    public RodSettings getRodSettings(RodType rodType) {
        RodSettings settings = rodSettings.get(rodType);
        if (settings == null) {
            return RodSettings.defaultOf(rodType);
        }
        return settings;
    }

    public List<String> getEnabledRodIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (RodType type : RodType.values()) {
            RodSettings settings = getRodSettings(type);
            if (settings.isEnabled()) {
                ids.add(type.getId());
            }
        }
        return ids;
    }

    public int getTopSize() {
        return topSize;
    }

    public boolean isCustomFishRequireCustomModelData() {
        return customFishRequireCustomModelData;
    }

    public String getCustomFishPdcNamespace() {
        return customFishPdcNamespace;
    }

    public String getCustomFishPdcKey() {
        return customFishPdcKey;
    }

    public String getCustomFishApiClass() {
        return customFishApiClass;
    }

    public String getCustomFishApiMethod() {
        return customFishApiMethod;
    }

    public String getFishNameTemplate() {
        return fishNameTemplate;
    }

    public List<String> getFishLoreTemplate() {
        return new ArrayList<String>(fishLoreTemplate);
    }

    public List<String> getRodLoreTemplate() {
        return new ArrayList<String>(rodLoreTemplate);
    }

    public List<String> getHelpMessages() {
        return new ArrayList<String>(helpMessages);
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public String message(String key, String defaultValue) {
        String value = messages.containsKey(key) ? messages.get(key) : defaultValue;
        return colorize(messagePrefix + value);
    }

    public String messageRaw(String key, String defaultValue) {
        String value = messages.containsKey(key) ? messages.get(key) : defaultValue;
        return colorize(value);
    }

    public String applyPlaceholders(String message, Map<String, String> placeholders) {
        String result = message;
        Set<Map.Entry<String, String>> entries = placeholders.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private void loadRarities(FileConfiguration config) {
        raritySettings.clear();
        for (FishRarity rarity : FishRarity.values()) {
            String path = "rarities." + rarity.getId();

            String displayName = config.getString(path + ".display_name", RaritySettings.defaultOf(rarity).getDisplayName());
            String color = config.getString(path + ".color", RaritySettings.defaultOf(rarity).getColorCode());
            int chance = Math.max(0, config.getInt(path + ".chance", RaritySettings.defaultOf(rarity).getChance()));
            double weightMin = Math.max(0.1D, config.getDouble(path + ".weight_min", RaritySettings.defaultOf(rarity).getWeightMin()));
            double weightMax = Math.max(weightMin, config.getDouble(path + ".weight_max", RaritySettings.defaultOf(rarity).getWeightMax()));
            double pricePerKg = Math.max(0.0D, config.getDouble(path + ".price_per_kg", RaritySettings.defaultOf(rarity).getPricePerKg()));

            raritySettings.put(rarity, new RaritySettings(rarity, displayName, color, chance, weightMin, weightMax, pricePerKg));
        }
    }

    private void loadRods(FileConfiguration config) {
        rodSettings.clear();
        for (RodType rodType : RodType.values()) {
            String path = "rods." + rodType.getId();
            RodSettings defaults = RodSettings.defaultOf(rodType);

            boolean enabled = config.getBoolean(path + ".enabled", defaults.isEnabled());
            String displayName = config.getString(path + ".display_name", defaults.getDisplayName());
            double weightBonus = config.getDouble(path + ".weight_bonus", defaults.getWeightBonus());
            double biteReduction = clamp(config.getDouble(path + ".bite_time_reduction", defaults.getBiteTimeReduction()), 0.0D, 0.95D);
            boolean useGlobalChances = config.getBoolean(path + ".use_global_chances", defaults.isUseGlobalChances());

            EnumMap<FishRarity, Integer> chances = new EnumMap<FishRarity, Integer>(FishRarity.class);
            if (!useGlobalChances) {
                ConfigurationSection section = config.getConfigurationSection(path + ".chances");
                if (section != null) {
                    chances.put(FishRarity.COMMON, Math.max(0, section.getInt("common", defaults.getChanceOrDefault(FishRarity.COMMON))));
                    chances.put(FishRarity.RARE, Math.max(0, section.getInt("rare", defaults.getChanceOrDefault(FishRarity.RARE))));
                    chances.put(FishRarity.EPIC, Math.max(0, section.getInt("epic", defaults.getChanceOrDefault(FishRarity.EPIC))));
                } else {
                    Map<FishRarity, Integer> defaultChances = defaults.getChancesOrNull();
                    if (defaultChances != null) {
                        chances.putAll(defaultChances);
                    } else {
                        chances.put(FishRarity.COMMON, Integer.valueOf(getRaritySettings(FishRarity.COMMON).getChance()));
                        chances.put(FishRarity.RARE, Integer.valueOf(getRaritySettings(FishRarity.RARE).getChance()));
                        chances.put(FishRarity.EPIC, Integer.valueOf(getRaritySettings(FishRarity.EPIC).getChance()));
                    }
                }
            }

            rodSettings.put(rodType, new RodSettings(rodType, enabled, displayName, weightBonus, biteReduction, useGlobalChances, chances));
        }
    }

    private void loadFormats(FileConfiguration config) {
        fishNameTemplate = config.getString("items.fish_name", "{rarity} fish");

        fishLoreTemplate.clear();
        List<String> fishLore = config.getStringList("items.fish_lore");
        if (fishLore.isEmpty()) {
            fishLoreTemplate.add("&7Weight: {weight} kg");
            fishLoreTemplate.add("&7Price: {price} coins");
        } else {
            fishLoreTemplate.addAll(fishLore);
        }

        rodLoreTemplate.clear();
        List<String> rodLore = config.getStringList("items.rod_lore");
        if (rodLore.isEmpty()) {
            rodLoreTemplate.add("&7Weight bonus: +{weight_bonus} kg");
            rodLoreTemplate.add("&7Bite speed: -{bite_reduction_percent}%");
            rodLoreTemplate.add("&8ID: {rod_id}");
        } else {
            rodLoreTemplate.addAll(rodLore);
        }
    }

    private void loadCustomFish(FileConfiguration config) {
        customFishRequireCustomModelData = config.getBoolean("custom_fish.require_custom_model_data", true);
        customFishPdcNamespace = trimToEmpty(config.getString("custom_fish.pdc_namespace", ""));
        customFishPdcKey = trimToEmpty(config.getString("custom_fish.pdc_key", ""));
        customFishApiClass = trimToEmpty(config.getString("custom_fish.api_class", ""));
        customFishApiMethod = trimToEmpty(config.getString("custom_fish.api_method", ""));
    }

    private void loadMessages(FileConfiguration config) {
        messages.clear();
        helpMessages.clear();

        messages.put("only_player", config.getString("messages.only_player", "&cThis command is only for players."));
        messages.put("no_permission", config.getString("messages.no_permission", "&cNo permission: {permission}"));
        messages.put("no_custom_fish", config.getString("messages.no_custom_fish", "&eYou do not have custom fish to sell."));
        messages.put("sold", config.getString("messages.sold", "&aSold fish: {count}, earned: {price} coins."));
        messages.put("economy_error", config.getString("messages.economy_error", "&cEconomy error: {error}"));
        messages.put("player_not_found", config.getString("messages.player_not_found", "&cPlayer not found or offline."));
        messages.put("invalid_rod", config.getString("messages.invalid_rod", "&cUnknown rod. Available: {available_rods}"));
        messages.put("give_usage", config.getString("messages.give_usage", "&eUsage: /fish give <player> <rod_id>"));
        messages.put("stats_usage", config.getString("messages.stats_usage", "&eUsage: /fish stats <player>"));
        messages.put("given_sender", config.getString("messages.given_sender", "&aYou gave {player}: {rod_id}"));
        messages.put("given_target", config.getString("messages.given_target", "&aYou received {rod_name}"));
        messages.put("stats_header", config.getString("messages.stats_header", "&6Stats: &e{player}"));
        messages.put("stats_total", config.getString("messages.stats_total", "&7Total fish: &f{total_fish}"));
        messages.put("stats_biggest", config.getString("messages.stats_biggest", "&7Biggest fish: &f{biggest_fish} kg"));
        messages.put("stats_money", config.getString("messages.stats_money", "&7Money earned: &f{money_earned}"));
        messages.put("top_header", config.getString("messages.top_header", "&6Top fishers (money):"));
        messages.put("top_empty", config.getString("messages.top_empty", "&eTop is empty."));
        messages.put("top_line", config.getString("messages.top_line", "&e{position}. &f{name} &7- {money} coins &8({fish_count} fish)"));
        messages.put("reload_done", config.getString("messages.reload_done", "&aNAMC-Fish config reloaded."));
        messages.put("catch", config.getString("messages.catch", "&aYou caught: {rarity}&a ({weight} kg, {price} coins)"));

        List<String> help = config.getStringList("messages.help");
        if (help.isEmpty()) {
            helpMessages.add("&6NAMC-Fish commands:");
            helpMessages.add("&e/fish sell");
            helpMessages.add("&e/fish top");
            helpMessages.add("&e/fish give <player> <rod_id>");
            helpMessages.add("&e/fish stats <player>");
            helpMessages.add("&e/fish reload");
        } else {
            helpMessages.addAll(help);
        }
    }

    private EnumMap<FishRarity, Integer> getGlobalChances() {
        EnumMap<FishRarity, Integer> global = new EnumMap<FishRarity, Integer>(FishRarity.class);
        for (FishRarity rarity : FishRarity.values()) {
            global.put(rarity, Integer.valueOf(getRaritySettings(rarity).getChance()));
        }
        return global;
    }

    private FishRarity rollByChances(Random random, Map<FishRarity, Integer> chances) {
        int common = chanceOf(chances, FishRarity.COMMON);
        int rare = chanceOf(chances, FishRarity.RARE);
        int epic = chanceOf(chances, FishRarity.EPIC);
        int total = common + rare + epic;

        if (total <= 0) {
            return FishRarity.COMMON;
        }

        int roll = random.nextInt(total);
        if (roll < common) {
            return FishRarity.COMMON;
        }
        if (roll < common + rare) {
            return FishRarity.RARE;
        }
        return FishRarity.EPIC;
    }

    private static int chanceOf(Map<FishRarity, Integer> chances, FishRarity rarity) {
        Integer value = chances.get(rarity);
        return Math.max(0, value == null ? 0 : value.intValue());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class RaritySettings {
        private final FishRarity rarity;
        private final String displayName;
        private final String colorCode;
        private final int chance;
        private final double weightMin;
        private final double weightMax;
        private final double pricePerKg;

        public RaritySettings(FishRarity rarity, String displayName, String colorCode, int chance, double weightMin, double weightMax, double pricePerKg) {
            this.rarity = rarity;
            this.displayName = displayName;
            this.colorCode = colorCode;
            this.chance = chance;
            this.weightMin = weightMin;
            this.weightMax = weightMax;
            this.pricePerKg = pricePerKg;
        }

        public FishRarity getRarity() {
            return rarity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }

        public int getChance() {
            return chance;
        }

        public double getWeightMin() {
            return weightMin;
        }

        public double getWeightMax() {
            return weightMax;
        }

        public double getPricePerKg() {
            return pricePerKg;
        }

        public String getColoredName() {
            return ChatColor.translateAlternateColorCodes('&', colorCode) + displayName;
        }

        public static RaritySettings defaultOf(FishRarity rarity) {
            if (rarity == FishRarity.RARE) {
                return new RaritySettings(rarity, "Rare", "&b", 25, 2.0D, 6.0D, 40.0D);
            }
            if (rarity == FishRarity.EPIC) {
                return new RaritySettings(rarity, "Epic", "&d", 5, 5.0D, 12.0D, 150.0D);
            }
            return new RaritySettings(rarity, "Common", "&f", 70, 1.0D, 3.0D, 10.0D);
        }
    }

    public static final class RodSettings {
        private final RodType rodType;
        private final boolean enabled;
        private final String displayName;
        private final double weightBonus;
        private final double biteTimeReduction;
        private final boolean useGlobalChances;
        private final EnumMap<FishRarity, Integer> chances;

        public RodSettings(RodType rodType, boolean enabled, String displayName, double weightBonus, double biteTimeReduction, boolean useGlobalChances, EnumMap<FishRarity, Integer> chances) {
            this.rodType = rodType;
            this.enabled = enabled;
            this.displayName = displayName;
            this.weightBonus = weightBonus;
            this.biteTimeReduction = biteTimeReduction;
            this.useGlobalChances = useGlobalChances;
            this.chances = chances == null ? new EnumMap<FishRarity, Integer>(FishRarity.class) : new EnumMap<FishRarity, Integer>(chances);
        }

        public RodType getRodType() {
            return rodType;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getWeightBonus() {
            return weightBonus;
        }

        public double getBiteTimeReduction() {
            return biteTimeReduction;
        }

        public boolean isUseGlobalChances() {
            return useGlobalChances;
        }

        public EnumMap<FishRarity, Integer> getChancesOrNull() {
            if (useGlobalChances) {
                return null;
            }
            return new EnumMap<FishRarity, Integer>(chances);
        }

        public int getChanceOrDefault(FishRarity rarity) {
            Integer value = chances.get(rarity);
            if (value == null) {
                return RaritySettings.defaultOf(rarity).getChance();
            }
            return value.intValue();
        }

        public static RodSettings defaultOf(RodType rodType) {
            EnumMap<FishRarity, Integer> defaults = new EnumMap<FishRarity, Integer>(FishRarity.class);
            boolean useGlobal = true;
            String name = "Fisher Rod";
            double weightBonus = 0.5D;
            double biteReduction = 0.0D;

            if (rodType == RodType.ROD2) {
                name = "Master Rod";
                weightBonus = 1.0D;
                biteReduction = 0.10D;
                useGlobal = false;
                defaults.put(FishRarity.COMMON, Integer.valueOf(60));
                defaults.put(FishRarity.RARE, Integer.valueOf(35));
                defaults.put(FishRarity.EPIC, Integer.valueOf(5));
            } else if (rodType == RodType.ROD3) {
                name = "Ocean Rod";
                weightBonus = 2.0D;
                biteReduction = 0.20D;
                useGlobal = false;
                defaults.put(FishRarity.COMMON, Integer.valueOf(50));
                defaults.put(FishRarity.RARE, Integer.valueOf(40));
                defaults.put(FishRarity.EPIC, Integer.valueOf(10));
            }

            return new RodSettings(rodType, true, name, weightBonus, biteReduction, useGlobal, defaults);
        }
    }
}
