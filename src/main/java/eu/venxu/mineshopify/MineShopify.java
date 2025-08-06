package eu.venxu.mineshopify;

import eu.venxu.mineshopify.commands.CommandHandler;
import eu.venxu.mineshopify.notification.NotificationManager;
import eu.venxu.mineshopify.shopify.ParseManager;
import eu.venxu.mineshopify.shopify.ShopifyManager;
import eu.venxu.mineshopify.storage.MySQLStorage;
import eu.venxu.mineshopify.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class MineShopify extends JavaPlugin {

    private ShopifyManager shopifyManager;
    private StorageManager storageManager;
    private ParseManager parseManager;
    private NotificationManager notificationManager;
    public final static String PREFIX = "§e§lMINESHOPIFY §8• §7";

    @Override
    public void onEnable() {
        // Initialize configuration
        initConfig();
        
        // Log startup message
        logStartupMessage();

        // Register managers
        registerManagers();
        
        // Register commands
        registerCommands();
        
        // Log debug status
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Debug mode is enabled. Additional logging will be shown.");
        }
    }

    @Override
    public void onDisable() {
        // Close database connections if using MySQL
        if (storageManager != null && storageManager.getStorage() instanceof MySQLStorage) {
            MySQLStorage mysqlStorage = (MySQLStorage) storageManager.getStorage();
            try {
                mysqlStorage.closePool();
                getLogger().info("Database connections closed successfully.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error closing database connections", e);
            }
        }
        
        // Log shutdown message
        logShutdownMessage();
    }
    
    /**
     * Initialize the configuration file with defaults if it doesn't exist
     */
    private void initConfig() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Create config directory if it doesn't exist
        File configDir = getDataFolder();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // Add debug option if not present
        if (!getConfig().contains("debug")) {
            getConfig().set("debug", false);
            saveConfig();
        }
    }
    
    /**
     * Log the startup message to the console
     */
    private void logStartupMessage() {
        Bukkit.getConsoleSender().sendMessage(" ");
        Bukkit.getConsoleSender().sendMessage(" ");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "Plugin §bMineShopify §7is §aactivated.");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§8↠ §7https://mineshopify.com/");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "Copyright 2025 MineShopify by Marsways Digital Services");
        Bukkit.getConsoleSender().sendMessage(" ");
    }
    
    /**
     * Log the shutdown message to the console
     */
    private void logShutdownMessage() {
        Bukkit.getConsoleSender().sendMessage(PREFIX + "Plugin §bMineShopify §7is §4deactivated.");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "§8↠ §7https://mineshopify.com/");
        Bukkit.getConsoleSender().sendMessage(PREFIX + "Copyright 2025 MineShopify by Astrovio");
    }

    /**
     * Register all the managers used in the main class.
     */
    private void registerManagers() {
        // Initialize storage first as other managers depend on it
        storageManager = new StorageManager(this);
        
        // Initialize notification manager
        notificationManager = new NotificationManager(this);
        
        // Initialize Shopify manager
        shopifyManager = new ShopifyManager(this);
        
        // Initialize parse manager last as it depends on the other managers
        parseManager = new ParseManager(this);
    }
    
    /**
     * Register commands for the plugin.
     */
    private void registerCommands() {
        CommandHandler commandHandler = new CommandHandler(this);
        PluginCommand command = getCommand("mineshopify");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
            getLogger().info("Commands registered successfully.");
        } else {
            getLogger().warning("Failed to register commands. Command not found in plugin.yml.");
        }
    }

    /**
     * Get the manager of the Shopify API.
     *
     * @return The manager that handles the Shopify API.
     */
    public ShopifyManager getShopifyManager() {
        return shopifyManager;
    }

    /**
     * Get the manager of the storage.
     *
     * @return The manager that handles the storage.
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * Get the manager of the JSON parser.
     *
     * @return The manager that handles the Shopify Orders object parser.
     */
    public ParseManager getParseManager() {
        return parseManager;
    }
    
    /**
     * Get the notification manager.
     *
     * @return The manager that handles notifications.
     */
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }
}
