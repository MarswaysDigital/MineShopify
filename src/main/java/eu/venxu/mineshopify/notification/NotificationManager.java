package eu.venxu.mineshopify.notification;

import eu.venxu.mineshopify.MineShopify;
import eu.venxu.mineshopify.order.Order;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

/**
 * Manages notifications for the MineShopify plugin.
 */
public class NotificationManager {

    private final MineShopify mineShopify;
    private boolean enabled;
    private List<String> recipients;
    private String notificationMessage;

    /**
     * Initialize the notification manager.
     *
     * @param mineShopify The main plugin instance.
     */
    public NotificationManager(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        loadConfig();
    }

    /**
     * Load notification configuration from config.yml
     */
    public void loadConfig() {
        try {
            enabled = mineShopify.getConfig().getBoolean("notifications.enabled", true);
            recipients = mineShopify.getConfig().getStringList("notifications.recipients");
            notificationMessage = mineShopify.getConfig().getString("notifications.message", 
                    "&a[MineShopify] &e%player% &7hat &6%package% &7gekauft! (Bestellung: &e%order_id%&7)");
            
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Notification system initialized. Enabled: " + enabled);
            }
        } catch (Exception e) {
            mineShopify.getLogger().log(Level.SEVERE, "Error loading notification configuration", e);
            enabled = false;
        }
    }

    /**
     * Send a notification about a new order to configured recipients.
     *
     * @param order The order that was processed.
     */
    public void sendOrderNotification(Order order) {
        if (!enabled || order == null) {
            return;
        }

        String message = notificationMessage
                .replace("%player%", order.getUsername())
                .replace("%package%", order.getPackageName())
                .replace("%order_id%", order.getOrderId());
        
        // Translate color codes
        message = ChatColor.translateAlternateColorCodes('&', message);
        
        // Send to console
        mineShopify.getLogger().info(ChatColor.stripColor(message));
        
        // Send to configured players if they are online
        for (String recipient : recipients) {
            Player player = Bukkit.getPlayerExact(recipient);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Check if notifications are enabled.
     *
     * @return True if notifications are enabled, false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
