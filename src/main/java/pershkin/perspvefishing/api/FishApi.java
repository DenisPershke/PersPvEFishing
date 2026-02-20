package pershkin.perspvefishing.api;

import org.bukkit.inventory.ItemStack;
import pershkin.perspvefishing.PersPvEFishing;

public final class FishApi {

    private FishApi() {
    }

    public static boolean isCustomFish(ItemStack item) {
        PersPvEFishing plugin = PersPvEFishing.getInstance();
        if (plugin == null || plugin.getItemManager() == null) {
            return false;
        }
        return plugin.getItemManager().isCustomFish(item);
    }
}
