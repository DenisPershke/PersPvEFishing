package pershkin.perspvefishing.item;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pershkin.perspvefishing.PersPvEFishing;
import pershkin.perspvefishing.config.FishingConfig;
import pershkin.perspvefishing.model.FishCatch;
import pershkin.perspvefishing.model.FishRarity;
import pershkin.perspvefishing.model.RodType;
import pershkin.perspvefishing.util.HashingUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemManager {

    private final PersPvEFishing plugin;
    private final FishingConfig fishingConfig;
    private final NamespacedKey rodIdKey;
    private final NamespacedKey rodSignatureKey;
    private final NamespacedKey fishMarkerKey;
    private final NamespacedKey fishRarityKey;
    private final NamespacedKey fishWeightKey;
    private final NamespacedKey fishNonceKey;
    private final NamespacedKey fishSignatureKey;
    private final DecimalFormat oneDigitFormat = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private String signingSecret;
    private boolean customFishRequireCustomModelData = true;
    private String customFishPdcNamespace = "";
    private String customFishPdcKey = "";
    private Method customFishApiMethod;
    private boolean customFishApiBroken;

    public ItemManager(PersPvEFishing plugin, FishingConfig fishingConfig) {
        this.plugin = plugin;
        this.fishingConfig = fishingConfig;
        this.rodIdKey = new NamespacedKey(plugin, "rod_id");
        this.rodSignatureKey = new NamespacedKey(plugin, "rod_signature");
        this.fishMarkerKey = new NamespacedKey(plugin, "fish_marker");
        this.fishRarityKey = new NamespacedKey(plugin, "fish_rarity");
        this.fishWeightKey = new NamespacedKey(plugin, "fish_weight");
        this.fishNonceKey = new NamespacedKey(plugin, "fish_nonce");
        this.fishSignatureKey = new NamespacedKey(plugin, "fish_signature");
        reloadSigningSecret();
    }

    public void reloadSigningSecret() {
        this.signingSecret = plugin.getSigningSecret();
        reloadCustomFishIntegration();
    }

    public ItemStack createRod(RodType rodType) {
        FishingConfig.RodSettings rodSettings = fishingConfig.getRodSettings(rodType);
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(fishingConfig.colorize("&6" + rodSettings.getDisplayName()));
        List<String> lore = new ArrayList<String>();
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("weight_bonus", formatOneDigit(rodSettings.getWeightBonus()));
        placeholders.put("bite_reduction_percent", String.valueOf((int) Math.round(rodSettings.getBiteTimeReduction() * 100.0D)));
        placeholders.put("rod_id", rodType.getId());
        placeholders.put("rod_name", rodSettings.getDisplayName());
        for (String line : fishingConfig.getRodLoreTemplate()) {
            lore.add(fishingConfig.colorize(fishingConfig.applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(rodIdKey, PersistentDataType.STRING, rodType.getId());
        container.set(rodSignatureKey, PersistentDataType.STRING, signRod(rodType.getId()));

        item.setItemMeta(meta);
        return item;
    }

    public RodType getRodType(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String rodId = container.get(rodIdKey, PersistentDataType.STRING);
        String signature = container.get(rodSignatureKey, PersistentDataType.STRING);
        if (rodId == null || signature == null) {
            return null;
        }

        if (!signature.equals(signRod(rodId))) {
            return null;
        }

        RodType rodType = RodType.fromId(rodId);
        if (rodType == null) {
            return null;
        }
        if (!fishingConfig.getRodSettings(rodType).isEnabled()) {
            return null;
        }
        return rodType;
    }

    public ItemStack createFishItem(FishCatch fishCatch) {
        FishingConfig.RaritySettings raritySettings = fishingConfig.getRaritySettings(fishCatch.getRarity());
        ItemStack item = new ItemStack(Material.COD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("rarity", raritySettings.getColoredName());
        placeholders.put("rarity_name", raritySettings.getDisplayName());
        placeholders.put("weight", formatOneDigit(fishCatch.getWeight()));
        placeholders.put("price", trimPrice(fishCatch.getValue()));
        String fishName = fishingConfig.applyPlaceholders(fishingConfig.getFishNameTemplate(), placeholders);
        meta.setDisplayName(fishingConfig.colorize(fishName));

        List<String> lore = new ArrayList<String>();
        for (String line : fishingConfig.getFishLoreTemplate()) {
            lore.add(fishingConfig.colorize(fishingConfig.applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);

        long nonce = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        String weightFormatted = formatOneDigit(fishCatch.getWeight());
        String signature = signFish(fishCatch.getRarity().getId(), weightFormatted, nonce);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(fishMarkerKey, PersistentDataType.BYTE, (byte) 1);
        container.set(fishRarityKey, PersistentDataType.STRING, fishCatch.getRarity().getId());
        container.set(fishWeightKey, PersistentDataType.DOUBLE, fishCatch.getWeight());
        container.set(fishNonceKey, PersistentDataType.LONG, nonce);
        container.set(fishSignatureKey, PersistentDataType.STRING, signature);

        item.setItemMeta(meta);
        return item;
    }

    public FishData readFishData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte marker = container.get(fishMarkerKey, PersistentDataType.BYTE);
        String rarityId = container.get(fishRarityKey, PersistentDataType.STRING);
        Double weight = container.get(fishWeightKey, PersistentDataType.DOUBLE);
        Long nonce = container.get(fishNonceKey, PersistentDataType.LONG);
        String signature = container.get(fishSignatureKey, PersistentDataType.STRING);

        if (marker == null || marker.byteValue() != (byte) 1 || rarityId == null || weight == null || nonce == null || signature == null) {
            return null;
        }

        FishRarity rarity = FishRarity.fromId(rarityId);
        if (rarity == null || weight.doubleValue() <= 0.0D) {
            return null;
        }

        String formattedWeight = formatOneDigit(weight.doubleValue());
        String expectedSignature = signFish(rarityId, formattedWeight, nonce.longValue());
        if (!signature.equals(expectedSignature)) {
            return null;
        }

        return new FishData(rarity, roundOneDigit(weight.doubleValue()));
    }

    public boolean isCustomFish(ItemStack item) {
        return readFishData(item) != null;
    }

    public double readExternalFishPrice(ItemStack item, Player player) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return -1.0D;
        }
        if (customFishApiBroken || customFishApiMethod == null) {
            return -1.0D;
        }
        if (!passesCustomFishFilters(item)) {
            return -1.0D;
        }

        Object result;
        try {
            if (customFishApiMethod.getParameterTypes().length == 1) {
                result = customFishApiMethod.invoke(null, item);
            } else {
                result = customFishApiMethod.invoke(null, item, player);
            }
        } catch (IllegalAccessException e) {
            customFishApiBroken = true;
            plugin.getLogger().warning("custom_fish API method is not accessible: " + e.getMessage());
            return -1.0D;
        } catch (InvocationTargetException e) {
            customFishApiBroken = true;
            Throwable cause = e.getCause() == null ? e : e.getCause();
            plugin.getLogger().warning("custom_fish API method failed: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            return -1.0D;
        }

        double price = parsePrice(result);
        if (price <= 0.0D) {
            return -1.0D;
        }
        return roundTwoDigits(price);
    }

    public double calculateFishValue(FishData fishData) {
        double pricePerKg = fishingConfig.getRaritySettings(fishData.rarity).getPricePerKg();
        return roundTwoDigits(fishData.weight * pricePerKg);
    }

    public String formatOneDigit(double value) {
        return oneDigitFormat.format(roundOneDigit(value));
    }

    public String trimPrice(double value) {
        double rounded = roundTwoDigits(value);
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.US, "%.2f", rounded);
    }

    private void reloadCustomFishIntegration() {
        customFishRequireCustomModelData = fishingConfig.isCustomFishRequireCustomModelData();
        customFishPdcNamespace = fishingConfig.getCustomFishPdcNamespace();
        customFishPdcKey = fishingConfig.getCustomFishPdcKey();
        customFishApiMethod = null;
        customFishApiBroken = false;

        String apiClass = fishingConfig.getCustomFishApiClass();
        String apiMethod = fishingConfig.getCustomFishApiMethod();
        if (isBlank(apiClass) || isBlank(apiMethod)) {
            return;
        }

        if (isBlank(customFishPdcNamespace) != isBlank(customFishPdcKey)) {
            plugin.getLogger().warning("custom_fish pdc_namespace and pdc_key should be set together. PDC filter disabled.");
            customFishPdcNamespace = "";
            customFishPdcKey = "";
        }

        Class<?> clazz = loadApiClass(apiClass);
        if (clazz == null) {
            plugin.getLogger().warning("custom_fish API class not found: " + apiClass);
            return;
        }

        customFishApiMethod = findSupportedApiMethod(clazz, apiMethod);
        if (customFishApiMethod == null) {
            plugin.getLogger().warning("custom_fish API method not found or has unsupported signature: " + apiClass + "#" + apiMethod);
        }
    }

    private Method findSupportedApiMethod(Class<?> clazz, String methodName) {
        Method selected = null;
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!isReturnTypeSupported(method.getReturnType())) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0].isAssignableFrom(ItemStack.class)) {
                if (selected == null || selected.getParameterTypes().length < 1) {
                    selected = method;
                }
            } else if (params.length == 2 && params[0].isAssignableFrom(ItemStack.class) && params[1].isAssignableFrom(Player.class)) {
                selected = method;
            }
        }
        return selected;
    }

    private Class<?> loadApiClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
        }

        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        for (Plugin external : plugins) {
            try {
                ClassLoader loader = external.getClass().getClassLoader();
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private boolean passesCustomFishFilters(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (customFishRequireCustomModelData && !meta.hasCustomModelData()) {
            return false;
        }
        if (isBlank(customFishPdcNamespace) || isBlank(customFishPdcKey)) {
            return true;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getNamespace().equalsIgnoreCase(customFishPdcNamespace) && key.getKey().equals(customFishPdcKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReturnTypeSupported(Class<?> returnType) {
        return isNumericType(returnType) || String.class.equals(returnType);
    }

    private static boolean isNumericType(Class<?> type) {
        if (type.isPrimitive()) {
            return type == double.class || type == float.class || type == int.class || type == long.class || type == short.class || type == byte.class;
        }
        return Number.class.isAssignableFrom(type);
    }

    private static double parsePrice(Object value) {
        if (value == null) {
            return -1.0D;
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) ? number : -1.0D;
        }
        if (value instanceof String) {
            try {
                double number = Double.parseDouble(((String) value).trim());
                return Double.isFinite(number) ? number : -1.0D;
            } catch (NumberFormatException ignored) {
                return -1.0D;
            }
        }
        return -1.0D;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String signRod(String rodId) {
        return HashingUtil.sha256(signingSecret + "|rod|" + rodId);
    }

    private String signFish(String rarityId, String weight, long nonce) {
        return HashingUtil.sha256(signingSecret + "|fish|" + rarityId + "|" + weight + "|" + nonce);
    }

    private static double roundOneDigit(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static double roundTwoDigits(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    public static final class FishData {
        private final FishRarity rarity;
        private final double weight;

        public FishData(FishRarity rarity, double weight) {
            this.rarity = rarity;
            this.weight = weight;
        }

        public FishRarity getRarity() {
            return rarity;
        }

        public double getWeight() {
            return weight;
        }
    }
}
