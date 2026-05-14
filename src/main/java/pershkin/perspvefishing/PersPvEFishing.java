package pershkin.perspvefishing;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import pershkin.perspvefishing.command.FishCommand;
import pershkin.perspvefishing.config.FishingConfig;
import pershkin.perspvefishing.listener.FishingListener;
import pershkin.perspvefishing.security.CaptchaManager;
import pershkin.perspvefishing.item.ItemManager;
import pershkin.perspvefishing.storage.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PersPvEFishing extends JavaPlugin {

    private static PersPvEFishing instance;

    private FishingConfig fishingConfig;
    private ItemManager itemManager;
    private DatabaseManager databaseManager;
    private CaptchaManager captchaManager;
    private Economy economy;
    private String signingSecret = "";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        prepareConfigFile();

        fishingConfig = new FishingConfig(getConfig());
        prepareSecret();

        itemManager = new ItemManager(this, fishingConfig);
        captchaManager = new CaptchaManager(this);
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        setupEconomy();

        FishCommand fishCommand = new FishCommand(this);
        PluginCommand command = getCommand("fish");
        if (command != null) {
            command.setExecutor(fishCommand);
            command.setTabCompleter(fishCommand);
        }

        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        instance = null;
    }

    public void reloadPlugin() {
        reloadConfig();
        prepareConfigFile();
        fishingConfig.reload(getConfig());
        prepareSecret();
        if (itemManager != null) {
            itemManager.reloadSigningSecret();
        }
        if (captchaManager != null) {
            captchaManager.clear();
        }
    }

    public static PersPvEFishing getInstance() {
        return instance;
    }

    public FishingConfig getFishingConfig() {
        return fishingConfig;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CaptchaManager getCaptchaManager() {
        return captchaManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    private void prepareSecret() {
        signingSecret = getConfig().getString("security.signing_secret", "");
        if (signingSecret == null || signingSecret.trim().isEmpty()) {
            signingSecret = UUID.randomUUID().toString().replace("-", "");
            getConfig().set("security.signing_secret", signingSecret);
            saveConfig();
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        economy = provider == null ? null : provider.getProvider();
    }

    private void prepareConfigFile() {
        if (!getConfig().contains("gameplay.vanilla_rod.catch_custom_fish")
                && getConfig().contains("gameplay.vanilla_rod.enabled")) {
            getConfig().set("gameplay.vanilla_rod.catch_custom_fish",
                    getConfig().getBoolean("gameplay.vanilla_rod.enabled", true));
            getConfig().set("gameplay.vanilla_rod.enabled", null);
        }
        getConfig().options().copyDefaults(true);
        replaceOldNames(getConfig());
        saveConfig();
    }

    private boolean replaceOldNames(ConfigurationSection section) {
        boolean changed = false;
        List<String> keys = new ArrayList<String>(section.getKeys(true));
        for (String key : keys) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            if (value instanceof String) {
                String fixed = fixName((String) value);
                if (!fixed.equals(value)) {
                    section.set(key, fixed);
                    changed = true;
                }
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                ArrayList<Object> fixedList = new ArrayList<Object>();
                boolean listChanged = false;
                for (Object item : list) {
                    if (item instanceof String) {
                        String fixed = fixName((String) item);
                        fixedList.add(fixed);
                        listChanged = listChanged || !fixed.equals(item);
                    } else {
                        fixedList.add(item);
                    }
                }
                if (listChanged) {
                    section.set(key, fixedList);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private String fixName(String value) {
        return value.replace("NAMC-Fish", "Fish")
                .replace("NAMC", "Fish")
                .replace("[PFishPve]", "[Fish]")
                .replace("PFishPve commands", "Fish commands")
                .replace("PFishPve команды", "Fish команды")
                .replace("PFishPve config", "Fish config")
                .replace("Конфиг PFishPve", "Конфиг Fish");
    }
}
