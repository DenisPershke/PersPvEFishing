package pershkin.perspvefishing.security;

import org.bukkit.entity.Player;
import pershkin.perspvefishing.PersPvEFishing;
import pershkin.perspvefishing.config.FishingConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class CaptchaManager {

    public static final int OK = 1;
    public static final int WRONG = 0;
    public static final int NO_CAPTCHA = -1;

    private final PersPvEFishing plugin;
    private final Random random = new Random();
    private final Map<UUID, Captcha> captchas = new HashMap<UUID, Captcha>();
    private final Map<UUID, Integer> catches = new HashMap<UUID, Integer>();

    public CaptchaManager(PersPvEFishing plugin) {
        this.plugin = plugin;
    }

    public boolean check(Player player) {
        FishingConfig config = plugin.getFishingConfig();
        if (!config.isCaptchaEnabled()) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        Captcha old = captchas.get(uuid);
        if (old != null) {
            if (old.isExpired()) {
                captchas.remove(uuid);
                send(player, "captcha_expired", "&cCaptcha expired. Try fishing again.", null);
                return false;
            }
            sendCaptcha(player, old);
            return false;
        }

        int count = catches.containsKey(uuid) ? catches.get(uuid).intValue() + 1 : 1;
        catches.put(uuid, Integer.valueOf(count));
        if (count < config.getCaptchaMinCatches()) {
            return true;
        }

        int chance = config.getCaptchaChancePercent();
        if (chance <= 0 || random.nextInt(100) >= chance) {
            return true;
        }

        catches.put(uuid, Integer.valueOf(0));
        Captcha captcha = new Captcha(String.valueOf(1000 + random.nextInt(9000)),
                System.currentTimeMillis() + config.getCaptchaTimeoutSeconds() * 1000L);
        captchas.put(uuid, captcha);
        sendCaptcha(player, captcha);
        return false;
    }

    public int answer(Player player, String code) {
        UUID uuid = player.getUniqueId();
        Captcha captcha = captchas.get(uuid);
        if (captcha == null || captcha.isExpired()) {
            captchas.remove(uuid);
            return NO_CAPTCHA;
        }
        if (!captcha.code.equals(code)) {
            return WRONG;
        }

        captchas.remove(uuid);
        catches.put(uuid, Integer.valueOf(0));
        return OK;
    }

    public void clear() {
        captchas.clear();
        catches.clear();
    }

    private void sendCaptcha(Player player, Captcha captcha) {
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("code", captcha.code);
        placeholders.put("seconds", String.valueOf(plugin.getFishingConfig().getCaptchaTimeoutSeconds()));
        send(player, "captcha_required", "&eEnter /fish captcha {code} to continue fishing.", placeholders);
    }

    private void send(Player player, String key, String def, Map<String, String> placeholders) {
        FishingConfig config = plugin.getFishingConfig();
        String message = config.message(key, def);
        if (placeholders != null) {
            message = config.applyPlaceholders(message, placeholders);
        }
        player.sendMessage(message);
    }

    private static final class Captcha {
        private final String code;
        private final long expiresAt;

        private Captcha(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
