package pershkin.perspvefishing.model;

import java.util.UUID;

public final class TopEntry {

    private final UUID uuid;
    private final double moneyEarned;
    private final int totalFish;
    private final double biggestFish;

    public TopEntry(UUID uuid, double moneyEarned, int totalFish, double biggestFish) {
        this.uuid = uuid;
        this.moneyEarned = moneyEarned;
        this.totalFish = totalFish;
        this.biggestFish = biggestFish;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getMoneyEarned() {
        return moneyEarned;
    }

    public int getTotalFish() {
        return totalFish;
    }

    public double getBiggestFish() {
        return biggestFish;
    }
}
