package eu.venxu.mineshopify.storage;

import eu.venxu.mineshopify.MineShopify;
import eu.venxu.mineshopify.order.Order;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class FileStorage implements IStorage {

    private MineShopify mineShopify;
    private File customConfigFile;
    private FileConfiguration customConfig;

    /**
     * Define MineShopify main class. Runs the connection.
     *
     * @param mineShopify The main class.
     */
    public FileStorage(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        createConnection();
    }

    /**
     * Create the connection of the File Storage.
     */
    @Override
    public void createConnection() {
        try {
            customConfigFile = new File(mineShopify.getDataFolder(), "orders.yml");
            if (!customConfigFile.exists()) {
                customConfigFile.getParentFile().mkdirs();
                mineShopify.saveResource("orders.yml", false);
            }

            customConfig = new YamlConfiguration();
            customConfig.load(customConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            Bukkit.getLogger().severe("Error connecting to the File.");
        }
    }

    /**
     * Add an order to the Orders File.
     */
    @Override
    public void addOrder(Order order) {
        try {
            customConfig.set(order.getId() + ".username", order.getUsername());
            customConfig.set(order.getId() + ".packageName", order.getPackageName());
            customConfig.set(order.getId() + ".orderId", order.getOrderId());
            customConfig.save(customConfigFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Error connecting to the File.");
        }
    }

    /**
     * Check if the order already exists in the Orders File.
     *
     * @param orderId The id of the order which has to be checked.
     *
     * @return The status of the order already existing.
     */
    @Override
    public boolean checkOrder(String orderId) {
        for(String orders : customConfig.getKeys(false)) {
            String orderIdString = customConfig.getString(orders + ".orderId");
            if(orderIdString != null && orderIdString.equals(orderId)) return true;
        }
        return false;
    }
}
