package eu.venxu.mineshopify.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.venxu.mineshopify.MineShopify;
import eu.venxu.mineshopify.order.Order;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MySQLStorage implements IStorage {

    private final MineShopify mineShopify;
    private HikariDataSource dataSource;
    
    // SQL statements
    private static final String CREATE_TABLE_SQL = 
            "CREATE TABLE IF NOT EXISTS orders(id varchar(64) NOT NULL, username varchar(64), packageName varchar(64), orderId varchar(64), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_order_id (orderId));";
    private static final String INSERT_ORDER_SQL = 
            "INSERT INTO orders(id, username, packageName, orderId) VALUES (?, ?, ?, ?);";
    private static final String CHECK_ORDER_SQL = 
            "SELECT 1 FROM orders WHERE orderId=? LIMIT 1";

    /**
     * Initialize MySQL storage with connection pooling.
     *
     * @param mineShopify The main plugin instance.
     */
    public MySQLStorage(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        createConnection();
    }

    /**
     * Create the connection pool for MySQL storage.
     * Uses HikariCP for efficient connection pooling.
     */
    @Override
    public void createConnection() {
        try {
            // Get database configuration from config
            String host = mineShopify.getConfig().getString("storage.host", "localhost");
            int port = mineShopify.getConfig().getInt("storage.port", 3306);
            String database = mineShopify.getConfig().getString("storage.database", "minecraft");
            String username = mineShopify.getConfig().getString("storage.username", "root");
            String password = mineShopify.getConfig().getString("storage.password", "");
            
            // Configure HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // Connection pool settings from config
            int maxConnections = mineShopify.getConfig().getInt("storage.pool.max_connections", 10);
            int minIdle = mineShopify.getConfig().getInt("storage.pool.min_idle", 3);
            int idleTimeout = mineShopify.getConfig().getInt("storage.pool.idle_timeout", 10);
            int maxLifetime = mineShopify.getConfig().getInt("storage.pool.max_lifetime", 30);
            
            config.setMaximumPoolSize(maxConnections); // Maximum number of connections
            config.setMinimumIdle(minIdle); // Minimum number of idle connections
            config.setIdleTimeout(TimeUnit.MINUTES.toMillis(idleTimeout)); // How long a connection can be idle
            config.setMaxLifetime(TimeUnit.MINUTES.toMillis(maxLifetime)); // Maximum lifetime of a connection
            config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(30)); // Connection timeout
            
            // Connection test query
            config.setConnectionTestQuery("SELECT 1");
            
            // Pool name for easier debugging
            config.setPoolName("MineShopifyPool");
            
            // Create the data source
            dataSource = new HikariDataSource(config);
            
            // Create the table if it doesn't exist
            createTable();
            
            mineShopify.getLogger().info("Successfully connected to MySQL database.");
            
        } catch (Exception e) {
            mineShopify.getLogger().log(Level.SEVERE, "Failed to connect to MySQL database", e);
            mineShopify.getLogger().severe("Falling back to file storage.");
            mineShopify.getStorageManager().changeStorage();
        }
    }

    /**
     * Create the orders table if it doesn't exist.
     * Added index on orderId for faster lookups.
     */
    private void createTable() {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            mineShopify.getLogger().log(Level.SEVERE, "Failed to create orders table", e);
        }
    }

    /**
     * Add an order to the MySQL database.
     * Uses connection pooling for better performance.
     * 
     * @param order The order to add to the database.
     */
    @Override
    public void addOrder(Order order) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_ORDER_SQL)) {
            
            stmt.setString(1, order.getId().toString());
            stmt.setString(2, order.getUsername());
            stmt.setString(3, order.getPackageName());
            stmt.setString(4, order.getOrderId());
            
            stmt.executeUpdate();
            
            // Log success if debug is enabled
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Order " + order.getOrderId() + " successfully saved to database.");
            }
            
        } catch (SQLException e) {
            mineShopify.getLogger().log(Level.SEVERE, "Failed to add order " + order.getOrderId() + " to database", e);
        }
    }

    /**
     * Check if an order already exists in the database.
     * Optimized query using LIMIT 1 for better performance.
     * 
     * @param orderId The ID of the order to check.
     * @return True if the order exists, false otherwise.
     */
    @Override
    public boolean checkOrder(String orderId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(CHECK_ORDER_SQL)) {
            
            stmt.setString(1, orderId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // If there's a result, the order exists
            }
            
        } catch (SQLException e) {
            mineShopify.getLogger().log(Level.SEVERE, "Failed to check if order " + orderId + " exists", e);
            return false; // Assume order doesn't exist on error
        }
    }
    
    /**
     * Get a connection from the connection pool.
     * 
     * @return A database connection.
     * @throws SQLException If a connection cannot be obtained.
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available.");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Close the connection pool when the plugin is disabled.
     * This method should be called from the main plugin class's onDisable method.
     */
    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
