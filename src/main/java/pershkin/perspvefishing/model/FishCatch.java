package pershkin.perspvefishing.model;

public final class FishCatch {

    private final FishRarity rarity;
    private final double weight;
    private final double value;

    public FishCatch(FishRarity rarity, double weight, double value) {
        this.rarity = rarity;
        this.weight = weight;
        this.value = value;
    }

    public FishRarity getRarity() {
        return rarity;
    }

    public double getWeight() {
        return weight;
    }

    public double getValue() {
        return value;
    }
}
