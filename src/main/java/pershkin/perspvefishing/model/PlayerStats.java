package pershkin.perspvefishing.model;

public final class PlayerStats {

    private final int totalFish;
    private final double biggestFish;
    private final double moneyEarned;

    public PlayerStats(int totalFish, double biggestFish, double moneyEarned) {
        this.totalFish = totalFish;
        this.biggestFish = biggestFish;
        this.moneyEarned = moneyEarned;
    }

    public int getTotalFish() {
        return totalFish;
    }

    public double getBiggestFish() {
        return biggestFish;
    }

    public double getMoneyEarned() {
        return moneyEarned;
    }
}
