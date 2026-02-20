package pershkin.perspvefishing.model;

import org.bukkit.ChatColor;

public enum FishRarity {
    COMMON("common", "Обычная", ChatColor.WHITE, 1.0D, 3.0D),
    RARE("rare", "Редкая", ChatColor.AQUA, 2.0D, 6.0D),
    EPIC("epic", "Эпическая", ChatColor.LIGHT_PURPLE, 5.0D, 12.0D);

    private final String id;
    private final String displayName;
    private final ChatColor color;
    private final double minWeight;
    private final double maxWeight;

    FishRarity(String id, String displayName, ChatColor color, double minWeight, double maxWeight) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public double getMinWeight() {
        return minWeight;
    }

    public double getMaxWeight() {
        return maxWeight;
    }

    public String getItemName() {
        return color + displayName + " рыба";
    }

    public static FishRarity fromId(String id) {
        if (id == null) {
            return null;
        }
        for (FishRarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(id)) {
                return rarity;
            }
        }
        return null;
    }
}
