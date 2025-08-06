package eu.venxu.mineshopify.shopify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import eu.venxu.mineshopify.MineShopify;
import eu.venxu.mineshopify.order.Order;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ParseManager {

    private final MineShopify mineShopify;
    private final Map<String, Long> processedOrderTimestamps;
    private final Map<String, ConfigurationSection> packageCache;
    
    // Constants for JSON field names to avoid typos and improve maintainability
    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_ORDERS = "orders";
    private static final String FIELD_ORDER_NUMBER = "order_number";
    private static final String FIELD_NOTE_ATTRIBUTES = "note_attributes";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_LINE_ITEMS = "line_items";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_QUANTITY = "quantity";
    private static final String PLACEHOLDER_PLAYER = "%player%";

    /**
     * Initialize the ParseManager with caching capabilities.
     *
     * @param mineShopify The main plugin instance.
     */
    public ParseManager(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        this.processedOrderTimestamps = new ConcurrentHashMap<>();
        this.packageCache = new HashMap<>();
    }

    /**
     * Parse orders from the Shopify API response and execute corresponding commands.
     * This method includes improved error handling, caching, and performance optimizations.
     */
    public void parseOrders() {
        // Get the JSON response from the Shopify manager
        JsonObject jsonObject = mineShopify.getShopifyManager().getResponse();
        if (jsonObject == null) {
            mineShopify.getLogger().warning("No response received from Shopify API.");
            return;
        }
        
        // Check for API errors
        if (jsonObject.has(FIELD_ERRORS)) {
            mineShopify.getLogger().warning("Shopify API returned errors: " + jsonObject.get(FIELD_ERRORS));
            return;
        }
        
        // Ensure the response contains orders
        if (!jsonObject.has(FIELD_ORDERS)) {
            mineShopify.getLogger().warning("Shopify API response does not contain orders field.");
            return;
        }
        
        // Get the orders array
        JsonArray orderArray;
        try {
            orderArray = jsonObject.getAsJsonArray(FIELD_ORDERS);
        } catch (Exception e) {
            mineShopify.getLogger().log(Level.SEVERE, "Failed to parse orders array", e);
            return;
        }
        
        // Log order count if debug is enabled
        if (mineShopify.getConfig().getBoolean("debug", false)) {
            mineShopify.getLogger().info("Processing " + orderArray.size() + " orders from Shopify.");
        }
        
        // Process each order
        for (int i = 0; i < orderArray.size(); i++) {
            try {
                processOrder(orderArray.get(i).getAsJsonObject());
            } catch (Exception e) {
                mineShopify.getLogger().log(Level.SEVERE, "Error processing order at index " + i, e);
            }
        }
        
        // Clean up old processed order timestamps (older than 30 days)
        cleanupProcessedOrders();
    }
    
    /**
     * Extract username from various possible locations in the order JSON.
     * 
     * @param orderJson The order JSON object
     * @return The extracted username or null if not found
     */
    private String extractUsername(JsonObject orderJson) {
        String username = null;
        
        // Debug-Ausgabe der gesamten Bestellung, wenn Debug aktiviert ist
        if (mineShopify.getConfig().getBoolean("debug", false)) {
            mineShopify.getLogger().info("Extracting username from order: " + 
                orderJson.toString().substring(0, Math.min(200, orderJson.toString().length())) + "...");
        }
        
        // Try to find username in line item properties first (most common location)
        if (orderJson.has(FIELD_LINE_ITEMS)) {
            JsonArray lineItems = getJsonArrayFromJson(orderJson, FIELD_LINE_ITEMS);
            if (lineItems != null && lineItems.size() > 0) {
                for (int i = 0; i < lineItems.size(); i++) {
                    JsonObject lineItem = lineItems.get(i).getAsJsonObject();
                    
                    // Check for properties array
                    if (lineItem.has("properties")) {
                        JsonArray properties = getJsonArrayFromJson(lineItem, "properties");
                        if (properties != null) {
                            for (int j = 0; j < properties.size(); j++) {
                                JsonObject property = properties.get(j).getAsJsonObject();
                                String name = getStringFromJson(property, "name");
                                String value = getStringFromJson(property, "value");
                                
                                if (mineShopify.getConfig().getBoolean("debug", false)) {
                                    mineShopify.getLogger().info("Checking line item property: name=" + name + ", value=" + value);
                                }
                                
                                if (name != null && value != null && !value.isEmpty()) {
                                    // Erweiterte Liste von möglichen Feldnamen für den Benutzernamen
                                    if (name.equalsIgnoreCase("username") || 
                                        name.equalsIgnoreCase("minecraft username") || 
                                        name.equalsIgnoreCase("minecraft_username") ||
                                        name.equalsIgnoreCase("minecraft-username") ||
                                        name.equalsIgnoreCase("mc username") ||
                                        name.equalsIgnoreCase("mc-username") ||
                                        name.equalsIgnoreCase("mc_username") ||
                                        name.equalsIgnoreCase("ign") ||
                                        name.equalsIgnoreCase("spielername") ||
                                        name.equalsIgnoreCase("player") ||
                                        name.equalsIgnoreCase("player_name") ||
                                        name.equalsIgnoreCase("player-name") ||
                                        name.equalsIgnoreCase("playername")) {
                                        
                                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                                            mineShopify.getLogger().info("Found username in line item property: " + value);
                                        }
                                        return value;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check for properties as direct fields
                    if (lineItem.has("properties_username")) {
                        String value = getStringFromJson(lineItem, "properties_username");
                        if (value != null && !value.isEmpty()) {
                            if (mineShopify.getConfig().getBoolean("debug", false)) {
                                mineShopify.getLogger().info("Found username in properties_username field: " + value);
                            }
                            return value;
                        }
                    }
                    
                    // Check for other common property field names
                    String[] possiblePropertyFields = {
                        "properties_minecraft_username", "properties_mc_username", 
                        "properties_ign", "properties_spielername", "properties_player",
                        "properties_player_name", "properties_playername"
                    };
                    
                    for (String field : possiblePropertyFields) {
                        if (lineItem.has(field)) {
                            String value = getStringFromJson(lineItem, field);
                            if (value != null && !value.isEmpty()) {
                                if (mineShopify.getConfig().getBoolean("debug", false)) {
                                    mineShopify.getLogger().info("Found username in field " + field + ": " + value);
                                }
                                return value;
                            }
                        }
                    }
                }
            }
        }
        
        // Try note attributes
        JsonArray noteAttributes = getJsonArrayFromJson(orderJson, FIELD_NOTE_ATTRIBUTES);
        if (noteAttributes != null && noteAttributes.size() > 0) {
            for (int i = 0; i < noteAttributes.size(); i++) {
                JsonObject attribute = noteAttributes.get(i).getAsJsonObject();
                String name = getStringFromJson(attribute, "name");
                String value = getStringFromJson(attribute, FIELD_VALUE);
                
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("Checking note attribute: name=" + name + ", value=" + value);
                }
                
                if (name != null && value != null && !value.isEmpty()) {
                    // Erweiterte Liste von möglichen Feldnamen für den Benutzernamen
                    if (name.equalsIgnoreCase("username") || 
                        name.equalsIgnoreCase("minecraft username") || 
                        name.equalsIgnoreCase("minecraft_username") ||
                        name.equalsIgnoreCase("minecraft-username") ||
                        name.equalsIgnoreCase("mc username") ||
                        name.equalsIgnoreCase("mc-username") ||
                        name.equalsIgnoreCase("mc_username") ||
                        name.equalsIgnoreCase("ign") ||
                        name.equalsIgnoreCase("spielername") ||
                        name.equalsIgnoreCase("player") ||
                        name.equalsIgnoreCase("player_name") ||
                        name.equalsIgnoreCase("player-name") ||
                        name.equalsIgnoreCase("playername")) {
                        
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Found username in note attribute: " + value);
                        }
                        return value;
                    }
                }
            }
        }
        
        // Try customer object
        if (orderJson.has("customer")) {
            // Prüfe, ob das Element ein JsonObject ist
            JsonElement customerElement = orderJson.get("customer");
            if (customerElement != null && !customerElement.isJsonNull() && customerElement.isJsonObject()) {
                JsonObject customer = customerElement.getAsJsonObject();
                
                // Check customer note
                if (customer.has("note")) {
                    String note = getStringFromJson(customer, "note");
                    if (note != null && !note.isEmpty()) {
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Found customer note: " + note);
                        }
                        // Try to extract username from note
                        String extractedName = extractUsernameFromText(note);
                        if (extractedName != null) {
                            return extractedName;
                        }
                    }
                }
                
                // Check other customer fields
                String[] customerFields = {"first_name", "last_name", "email", "phone"};
                for (String field : customerFields) {
                    if (customer.has(field)) {
                        String value = getStringFromJson(customer, field);
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Found customer." + field + ": " + value);
                        }
                    }
                }
            } else if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("customer is not a valid JsonObject");
            }
        }
        
        // Try note field directly
        if (orderJson.has("note")) {
            String note = getStringFromJson(orderJson, "note");
            if (note != null && !note.isEmpty()) {
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("Found potential username in order note: " + note);
                }
                return note;
            }
        }
        
        // Try attributes array
        if (orderJson.has("attributes")) {
            JsonElement attributesElement = orderJson.get("attributes");
            
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("attributes type: " + attributesElement.getClass().getName());
                mineShopify.getLogger().info("attributes content: " + attributesElement.toString());
            }
            
            // Prüfe, ob attributes ein JsonArray ist (Standard-Format)
            if (attributesElement.isJsonArray()) {
                JsonArray attributes = attributesElement.getAsJsonArray();
                if (attributes != null && attributes.size() > 0) {
                    for (int i = 0; i < attributes.size(); i++) {
                        JsonObject attribute = attributes.get(i).getAsJsonObject();
                        String name = getStringFromJson(attribute, "name");
                        String value = getStringFromJson(attribute, FIELD_VALUE);
                        
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Checking attribute: name=" + name + ", value=" + value);
                        }
                        
                        if (name != null && value != null && !value.isEmpty()) {
                            // Erweiterte Liste von möglichen Feldnamen für den Benutzernamen
                            if (name.equalsIgnoreCase("username") || 
                                name.equalsIgnoreCase("minecraft username") || 
                                name.equalsIgnoreCase("minecraft_username") ||
                                name.equalsIgnoreCase("minecraft-username") ||
                                name.equalsIgnoreCase("mc username") ||
                                name.equalsIgnoreCase("mc-username") ||
                                name.equalsIgnoreCase("mc_username") ||
                                name.equalsIgnoreCase("ign") ||
                                name.equalsIgnoreCase("spielername") ||
                                name.equalsIgnoreCase("player") ||
                                name.equalsIgnoreCase("player_name") ||
                                name.equalsIgnoreCase("player-name") ||
                                name.equalsIgnoreCase("playername")) {
                                
                                if (mineShopify.getConfig().getBoolean("debug", false)) {
                                    mineShopify.getLogger().info("Found username in attribute: " + value);
                                }
                                return value;
                            }
                        }
                    }
                }
            } 
            // Prüfe, ob attributes ein JsonObject ist (alternatives Format)
            else if (attributesElement.isJsonObject()) {
                JsonObject attributesObj = attributesElement.getAsJsonObject();
                
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("attributes keys: " + attributesObj.keySet());
                }
                
                // Direkt nach 'username' suchen (wie im HTML-Formular definiert)
                if (attributesObj.has("username")) {
                    String value = getStringFromJson(attributesObj, "username");
                    if (value != null && !value.isEmpty()) {
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Found username in attributes.username: " + value);
                        }
                        return value;
                    }
                }
                
                // Andere mögliche Feldnamen prüfen
                String[] possibleFields = {"minecraft_username", "minecraft-username", "mc_username", "ign", "spielername"};
                for (String field : possibleFields) {
                    if (attributesObj.has(field)) {
                        String value = getStringFromJson(attributesObj, field);
                        if (value != null && !value.isEmpty()) {
                            if (mineShopify.getConfig().getBoolean("debug", false)) {
                                mineShopify.getLogger().info("Found username in attributes." + field + ": " + value);
                            }
                            return value;
                        }
                    }
                }
            }
        }
        
        // Try cart_attributes
        if (orderJson.has("cart_attributes")) {
            // Prüfe, ob das Element ein JsonObject ist
            JsonElement cartAttributesElement = orderJson.get("cart_attributes");
            if (cartAttributesElement != null && !cartAttributesElement.isJsonNull()) {
                // Detaillierte Debug-Ausgabe für cart_attributes
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("cart_attributes type: " + cartAttributesElement.getClass().getName());
                    mineShopify.getLogger().info("cart_attributes content: " + cartAttributesElement.toString());
                }
                
                if (cartAttributesElement.isJsonObject()) {
                    JsonObject cartAttributes = cartAttributesElement.getAsJsonObject();
                    
                    // Debug-Ausgabe aller Schlüssel
                    if (mineShopify.getConfig().getBoolean("debug", false)) {
                        mineShopify.getLogger().info("cart_attributes keys: " + cartAttributes.keySet());
                    }
                    
                    // Direkt nach 'username' suchen (wie im HTML-Formular definiert)
                    if (cartAttributes.has("username")) {
                        String value = getStringFromJson(cartAttributes, "username");
                        if (value != null && !value.isEmpty()) {
                            if (mineShopify.getConfig().getBoolean("debug", false)) {
                                mineShopify.getLogger().info("Found username in cart_attributes.username: " + value);
                            }
                            return value;
                        }
                    }
                    
                    // Andere mögliche Feldnamen prüfen
                    String[] possibleFields = {"minecraft_username", "minecraft-username", "mc_username", "ign", "spielername"};
                    for (String field : possibleFields) {
                        if (cartAttributes.has(field)) {
                            String value = getStringFromJson(cartAttributes, field);
                            if (value != null && !value.isEmpty()) {
                                if (mineShopify.getConfig().getBoolean("debug", false)) {
                                    mineShopify.getLogger().info("Found username in cart_attributes." + field + ": " + value);
                                }
                                return value;
                            }
                        }
                    }
                } else if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("cart_attributes is not a JsonObject but: " + cartAttributesElement.getClass().getName());
                }
            } else if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("cart_attributes is null or JsonNull");
            }
        }
        
        // Try shipping_address and billing_address
        String[] addressTypes = {"shipping_address", "billing_address"};
        for (String addressType : addressTypes) {
            if (orderJson.has(addressType)) {
                // Prüfe, ob das Element ein JsonObject ist
                JsonElement addressElement = orderJson.get(addressType);
                if (addressElement != null && !addressElement.isJsonNull() && addressElement.isJsonObject()) {
                    JsonObject address = addressElement.getAsJsonObject();
                    if (address.has("name")) {
                        String name = getStringFromJson(address, "name");
                        if (mineShopify.getConfig().getBoolean("debug", false)) {
                            mineShopify.getLogger().info("Found name in " + addressType + ": " + name);
                        }
                    }
                } else if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info(addressType + " is not a valid JsonObject");
                }
            }
        }
        
        // Als letzten Versuch, prüfe auf Tags
        if (orderJson.has("tags")) {
            String tags = getStringFromJson(orderJson, "tags");
            if (tags != null && !tags.isEmpty()) {
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("Order tags: " + tags);
                }
            }
        }
        
        if (mineShopify.getConfig().getBoolean("debug", false)) {
            mineShopify.getLogger().warning("Could not find username in order");
        }
        return null;
    }
    
    /**
     * Process a single order from the Shopify API.
     * 
     * @param orderJson The JSON object representing the order.
     */
    private void processOrder(JsonObject orderJson) {
        try {
            // Log the order JSON for debugging
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Processing order: " + orderJson.toString().substring(0, Math.min(200, orderJson.toString().length())) + "...");
                
                // Durchsuche alle Felder nach möglichen Benutzernamen
                searchAllFieldsForUsername(orderJson, "");
            }
            
            // Extract order ID - try different fields that might contain the order number
            String orderId = null;
            
            // Try order_number first (standard field)
            orderId = getStringFromJson(orderJson, FIELD_ORDER_NUMBER);
            
            // If not found, try id field
            if (orderId == null) {
                orderId = getStringFromJson(orderJson, "id");
            }
            
            // If not found, try name field
            if (orderId == null) {
                orderId = getStringFromJson(orderJson, "name");
            }
            
            // If still not found, try order_id field
            if (orderId == null) {
                orderId = getStringFromJson(orderJson, "order_id");
            }
            
            if (orderId == null) {
                mineShopify.getLogger().warning("Order missing order number, skipping. Available fields: " + orderJson.keySet());
                return;
            }
            
            // Check if we've already processed this order
            if (mineShopify.getStorageManager().getStorage().checkOrder(orderId)) {
                // Order already processed, skip
                return;
            }
            
            // Log the entire order JSON for debugging
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Full order JSON for order " + orderId + ": " + orderJson.toString());
                
                // Detaillierte Debug-Ausgabe für wichtige Felder
                if (orderJson.has("attributes")) {
                    mineShopify.getLogger().info("Order " + orderId + " has 'attributes' field: " + orderJson.get("attributes"));
                } else {
                    mineShopify.getLogger().info("Order " + orderId + " has NO 'attributes' field");
                }
                
                if (orderJson.has("cart_attributes")) {
                    mineShopify.getLogger().info("Order " + orderId + " has 'cart_attributes' field: " + orderJson.get("cart_attributes"));
                } else {
                    mineShopify.getLogger().info("Order " + orderId + " has NO 'cart_attributes' field");
                }
                
                if (orderJson.has("note_attributes")) {
                    mineShopify.getLogger().info("Order " + orderId + " has 'note_attributes' field: " + orderJson.get("note_attributes"));
                } else {
                    mineShopify.getLogger().info("Order " + orderId + " has NO 'note_attributes' field");
                }
                
                // Suche speziell nach den Attributen aus dem HTML-Formular
                mineShopify.getLogger().info("[DEBUG] Suche nach HTML-Formular-Attributen in Bestellung " + orderId);
                searchForHtmlFormAttributes(orderJson);
            }
            
            // Use the new extractUsername method to get the username
            String username = extractUsername(orderJson);
            String accountType = "Java"; // Default to Java if not specified
            
            // Try to find account type in various locations
            // First check note_attributes
            JsonArray noteAttributes = getJsonArrayFromJson(orderJson, FIELD_NOTE_ATTRIBUTES);
            if (noteAttributes != null && noteAttributes.size() > 0) {
                for (int i = 0; i < noteAttributes.size(); i++) {
                    JsonObject attribute = noteAttributes.get(i).getAsJsonObject();
                    String name = getStringFromJson(attribute, "name");
                    String value = getStringFromJson(attribute, FIELD_VALUE);
                    
                    if (name != null && value != null && !value.isEmpty() && name.equalsIgnoreCase("account_type")) {
                        accountType = value;
                        break;
                    }
                }
            }
            
            // Then check line item properties for account type
            if (orderJson.has(FIELD_LINE_ITEMS)) {
                JsonArray lineItems = getJsonArrayFromJson(orderJson, FIELD_LINE_ITEMS);
                if (lineItems != null && lineItems.size() > 0) {
                    for (int i = 0; i < lineItems.size(); i++) {
                        JsonObject lineItem = lineItems.get(i).getAsJsonObject();
                        if (lineItem.has("properties")) {
                            JsonArray properties = getJsonArrayFromJson(lineItem, "properties");
                            if (properties != null && properties.size() > 0) {
                                for (int j = 0; j < properties.size(); j++) {
                                    JsonObject property = properties.get(j).getAsJsonObject();
                                    String name = getStringFromJson(property, "name");
                                    String value = getStringFromJson(property, "value");
                                    
                                    if (name != null && value != null && !value.isEmpty() && 
                                        (name.equalsIgnoreCase("account_type") || name.equalsIgnoreCase("minecraft_account_type"))) {
                                        accountType = value;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Log what we found
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Extracted username: " + username);
                mineShopify.getLogger().info("Extracted account type: " + accountType);
            }
            
            // Check if username was found
            if (username == null || username.isEmpty()) {
                // If debug is enabled, log more details about the order
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().warning("Order " + orderId + " has invalid or missing username, skipping. Order JSON: " + 
                            orderJson.toString().substring(0, Math.min(500, orderJson.toString().length())) + "...");
                } else {
                    mineShopify.getLogger().warning("Order " + orderId + " has invalid or missing username, skipping.");
                }
                return;
            }
            
            // Log the found username if debug is enabled
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Found username '" + username + "' for order " + orderId);
            }
            
            // Add prefix for Bedrock accounts if needed
            if (accountType.equals("Bedrock") && !username.startsWith("!")) {
                username = "!" + username;
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("Added Bedrock prefix to username: " + username);
                }
            }
            
            // Get line items (products purchased)
            JsonArray lineItems = getJsonArrayFromJson(orderJson, FIELD_LINE_ITEMS);
            if (lineItems == null || lineItems.size() == 0) {
                mineShopify.getLogger().warning("Order " + orderId + " has no line items, skipping.");
                return;
            }
            
            // Get the player object - verwende UUID wenn möglich, aber da wir nur den Namen haben, müssen wir die veraltete Methode verwenden
            // In einer zukünftigen Version könnte man hier eine UUID-Lookup-API verwenden
            String playerName = username;
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                if (player != null && player.getName() != null) {
                    playerName = player.getName();
                }
            } catch (Exception e) {
                mineShopify.getLogger().warning("Could not get player data for " + username + ", using username directly.");
            }
            
            // Process each line item (product)
            for (int i = 0; i < lineItems.size(); i++) {
                try {
                    processLineItem(lineItems.get(i).getAsJsonObject(), orderId, username, playerName);
                } catch (Exception e) {
                    mineShopify.getLogger().log(Level.SEVERE, "Error processing line item for order " + orderId, e);
                }
            }
            
            // Record timestamp of processing this order
            processedOrderTimestamps.put(orderId, System.currentTimeMillis());
            
        } catch (Exception e) {
            mineShopify.getLogger().log(Level.SEVERE, "Error processing order", e);
        }
    }
    
    /**
     * Process a single line item (product) from an order.
     * 
     * @param lineItem The JSON object representing the line item.
     * @param orderId The order ID.
     * @param username The Minecraft username.
     * @param playerName The resolved player name.
     */
    private void processLineItem(JsonObject lineItem, String orderId, String username, String playerName) {
        // Extract product name
        String productName = getStringFromJson(lineItem, FIELD_NAME);
        if (productName == null || productName.isEmpty()) {
            mineShopify.getLogger().warning("Line item in order " + orderId + " has no name, skipping.");
            return;
        }
        
        // Get the package configuration for this product
        ConfigurationSection packageConfig = getPackageConfig(productName);
        if (packageConfig == null) {
            // No package configuration found for this product
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("No package configuration found for product: " + productName);
            }
            return;
        }
        
        // Get commands to execute
        List<String> commands = packageConfig.getStringList("commands");
        if (commands.isEmpty()) {
            mineShopify.getLogger().warning("Package " + productName + " has no commands configured.");
            return;
        }
        
        // Get quantity
        int quantity = getIntFromJson(lineItem, FIELD_QUANTITY, 1);
        quantity = Math.max(1, quantity); // Ensure at least 1
        
        // Execute commands for each quantity
        executeCommands(commands, playerName, quantity);
        
        // Store the processed order
        Order order = new Order(username, productName, orderId);
        mineShopify.getStorageManager().getStorage().addOrder(order);
        
        // Send notification about the processed order
        mineShopify.getNotificationManager().sendOrderNotification(order);
        
        // Log successful processing if debug is enabled
        if (mineShopify.getConfig().getBoolean("debug", false)) {
            mineShopify.getLogger().info("Successfully processed order " + orderId + 
                    " for player " + username + ", product: " + productName);
        }
    }
    
    /**
     * Execute commands for a purchased product.
     * 
     * @param commands List of commands to execute.
     * @param playerName The player name to substitute in commands.
     * @param quantity The quantity of the product purchased.
     */
    private void executeCommands(List<String> commands, String playerName, int quantity) {
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        
        for (int i = 0; i < quantity; i++) {
            for (String command : commands) {
                try {
                    // Replace player placeholder and execute command
                    String finalCommand = command.replace(PLACEHOLDER_PLAYER, playerName);
                    Bukkit.dispatchCommand(console, finalCommand);
                    
                    // Log command execution if debug is enabled
                    if (mineShopify.getConfig().getBoolean("debug", false)) {
                        mineShopify.getLogger().info("Executed command: " + finalCommand);
                    }
                } catch (Exception e) {
                    mineShopify.getLogger().log(Level.SEVERE, "Failed to execute command: " + command, e);
                }
            }
        }
    }
    
    /**
     * Get package configuration from cache or config file.
     * 
     * @param packageName The name of the package/product.
     * @return The configuration section for the package, or null if not found.
     */
    private ConfigurationSection getPackageConfig(String packageName) {
        // Check cache first
        if (packageCache.containsKey(packageName)) {
            return packageCache.get(packageName);
        }
        
        // Get from config
        ConfigurationSection packages = mineShopify.getConfig().getConfigurationSection("packages");
        if (packages != null && packages.contains(packageName)) {
            ConfigurationSection packageConfig = packages.getConfigurationSection(packageName);
            // Cache for future use
            packageCache.put(packageName, packageConfig);
            return packageConfig;
        }
        
        return null;
    }
    
    /**
     * Clean up old processed order timestamps to prevent memory leaks.
     */
    private void cleanupProcessedOrders() {
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
        processedOrderTimestamps.entrySet().removeIf(entry -> entry.getValue() < thirtyDaysAgo);
    }
    
    /**
     * Sucht speziell nach den Attributen aus dem HTML-Formular.
     * Diese Methode ist nur für Debug-Zwecke gedacht.
     * 
     * @param orderJson Das JSON-Objekt der Bestellung
     */
    private void searchForHtmlFormAttributes(JsonObject orderJson) {
        // Prüfe verschiedene mögliche Strukturen für die Attribute
        
        // 1. Prüfe note_attributes
        if (orderJson.has("note_attributes")) {
            JsonElement noteAttributesElement = orderJson.get("note_attributes");
            if (noteAttributesElement != null && !noteAttributesElement.isJsonNull() && noteAttributesElement.isJsonArray()) {
                JsonArray noteAttributes = noteAttributesElement.getAsJsonArray();
                for (int i = 0; i < noteAttributes.size(); i++) {
                    if (noteAttributes.get(i).isJsonObject()) {
                        JsonObject attribute = noteAttributes.get(i).getAsJsonObject();
                        if (attribute.has("name") && attribute.has("value")) {
                            String name = getStringFromJson(attribute, "name");
                            String value = getStringFromJson(attribute, "value");
                            mineShopify.getLogger().info("[HTML-FORM] note_attributes[" + i + "].name = " + name);
                            mineShopify.getLogger().info("[HTML-FORM] note_attributes[" + i + "].value = " + value);
                        }
                    }
                }
            }
        }
        
        // 2. Prüfe attributes als JsonObject
        if (orderJson.has("attributes")) {
            JsonElement attributesElement = orderJson.get("attributes");
            if (attributesElement != null && !attributesElement.isJsonNull()) {
                if (attributesElement.isJsonObject()) {
                    JsonObject attributes = attributesElement.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
                        String key = entry.getKey();
                        JsonElement value = entry.getValue();
                        mineShopify.getLogger().info("[HTML-FORM] attributes." + key + " = " + value);
                    }
                } else if (attributesElement.isJsonArray()) {
                    JsonArray attributes = attributesElement.getAsJsonArray();
                    for (int i = 0; i < attributes.size(); i++) {
                        if (attributes.get(i).isJsonObject()) {
                            JsonObject attribute = attributes.get(i).getAsJsonObject();
                            if (attribute.has("name") && attribute.has("value")) {
                                String name = getStringFromJson(attribute, "name");
                                String value = getStringFromJson(attribute, "value");
                                mineShopify.getLogger().info("[HTML-FORM] attributes[" + i + "].name = " + name);
                                mineShopify.getLogger().info("[HTML-FORM] attributes[" + i + "].value = " + value);
                            }
                        }
                    }
                }
            }
        }
        
        // 3. Prüfe cart_attributes
        if (orderJson.has("cart_attributes")) {
            JsonElement cartAttributesElement = orderJson.get("cart_attributes");
            if (cartAttributesElement != null && !cartAttributesElement.isJsonNull() && cartAttributesElement.isJsonObject()) {
                JsonObject cartAttributes = cartAttributesElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : cartAttributes.entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();
                    mineShopify.getLogger().info("[HTML-FORM] cart_attributes." + key + " = " + value);
                }
            }
        }
        
        // 4. Prüfe line_items.properties
        if (orderJson.has("line_items")) {
            JsonElement lineItemsElement = orderJson.get("line_items");
            if (lineItemsElement != null && !lineItemsElement.isJsonNull() && lineItemsElement.isJsonArray()) {
                JsonArray lineItems = lineItemsElement.getAsJsonArray();
                for (int i = 0; i < lineItems.size(); i++) {
                    if (lineItems.get(i).isJsonObject()) {
                        JsonObject lineItem = lineItems.get(i).getAsJsonObject();
                        if (lineItem.has("properties")) {
                            JsonElement propertiesElement = lineItem.get("properties");
                            if (propertiesElement != null && !propertiesElement.isJsonNull()) {
                                if (propertiesElement.isJsonArray()) {
                                    JsonArray properties = propertiesElement.getAsJsonArray();
                                    for (int j = 0; j < properties.size(); j++) {
                                        if (properties.get(j).isJsonObject()) {
                                            JsonObject property = properties.get(j).getAsJsonObject();
                                            if (property.has("name") && property.has("value")) {
                                                String name = getStringFromJson(property, "name");
                                                String value = getStringFromJson(property, "value");
                                                mineShopify.getLogger().info("[HTML-FORM] line_items[" + i + "].properties[" + j + "].name = " + name);
                                                mineShopify.getLogger().info("[HTML-FORM] line_items[" + i + "].properties[" + j + "].value = " + value);
                                            }
                                        }
                                    }
                                } else if (propertiesElement.isJsonObject()) {
                                    JsonObject properties = propertiesElement.getAsJsonObject();
                                    for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                                        String key = entry.getKey();
                                        JsonElement value = entry.getValue();
                                        mineShopify.getLogger().info("[HTML-FORM] line_items[" + i + "].properties." + key + " = " + value);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Durchsucht rekursiv alle Felder eines JsonObjects nach möglichen Benutzernamen.
     * Diese Methode ist nur für Debug-Zwecke gedacht.
     * 
     * @param jsonElement Das zu durchsuchende JsonElement
     * @param path Der aktuelle Pfad im JSON
     */
    private void searchAllFieldsForUsername(JsonElement jsonElement, String path) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return;
        }
        
        // Wenn es ein JsonObject ist, durchsuche alle Felder
        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                String newPath = path.isEmpty() ? key : path + "." + key;
                
                // Prüfe, ob der Schlüssel auf einen Benutzernamen hinweist
                if (key.toLowerCase().contains("username") || 
                    key.toLowerCase().contains("user_name") || 
                    key.toLowerCase().contains("user-name") || 
                    key.toLowerCase().contains("ign") || 
                    key.toLowerCase().contains("spielername") || 
                    key.toLowerCase().contains("minecraft")) {
                    
                    mineShopify.getLogger().info("[DEBUG] Potenzielles Benutzernamenfeld gefunden: " + newPath + " = " + value);
                }
                
                // Rekursiv weitermachen
                searchAllFieldsForUsername(value, newPath);
            }
        }
        // Wenn es ein JsonArray ist, durchsuche alle Elemente
        else if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                searchAllFieldsForUsername(jsonArray.get(i), path + "[" + i + "]");
            }
        }
        // Wenn es ein primitiver Wert ist, prüfe, ob es ein Benutzername sein könnte
        else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isString()) {
                String value = primitive.getAsString();
                if (value != null && !value.isEmpty() && value.matches("^[\\w]{3,16}$")) {
                    // Sieht wie ein Benutzername aus (3-16 alphanumerische Zeichen)
                    mineShopify.getLogger().info("[DEBUG] Möglicher Benutzername gefunden in " + path + ": " + value);
                }
            }
        }
    }
    
    /**
     * Versucht, einen Benutzernamen aus einem Text zu extrahieren.
     * 
     * @param text Der Text, aus dem der Benutzername extrahiert werden soll
     * @return Der extrahierte Benutzername oder null, wenn keiner gefunden wurde
     */
    private String extractUsernameFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // Versuche, einen Benutzernamen zu finden, der typischerweise 3-16 Zeichen lang ist
        // und nur Buchstaben, Zahlen und Unterstriche enthält
        
        // Prüfe auf Muster wie "username: xyz" oder "ign: xyz"
        String[] patterns = {"username[\\s]*:[\\s]*([\\w]{3,16})", 
                           "ign[\\s]*:[\\s]*([\\w]{3,16})", 
                           "minecraft[\\s]*:[\\s]*([\\w]{3,16})",
                           "spielername[\\s]*:[\\s]*([\\w]{3,16})",
                           "mc[\\s]*:[\\s]*([\\w]{3,16})"};
        
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String username = m.group(1);
                if (mineShopify.getConfig().getBoolean("debug", false)) {
                    mineShopify.getLogger().info("Extracted username '" + username + "' from text using pattern: " + pattern);
                }
                return username;
            }
        }
        
        // Wenn kein Muster gefunden wurde, prüfe, ob der Text selbst ein gültiger Benutzername sein könnte
        if (text.matches("^[\\w]{3,16}$")) {
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Text itself appears to be a valid username: " + text);
            }
            return text;
        }
        
        return null;
    }
    
    /**
     * Safely extract a string from a JSON object.
     * 
     * @param json The JSON object.
     * @param key The key to extract.
     * @return The string value, or null if not found or not a string.
     */
    private String getStringFromJson(JsonObject json, String key) {
        try {
            if (json.has(key)) {
                JsonElement element = json.get(key);
                if (element.isJsonPrimitive()) {
                    JsonPrimitive primitive = element.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        return primitive.getAsString();
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle and return null
        }
        return null;
    }
    
    /**
     * Safely extract a JSON array from a JSON object.
     * 
     * @param json The JSON object.
     * @param key The key to extract.
     * @return The JSON array, or null if not found or not an array.
     */
    private JsonArray getJsonArrayFromJson(JsonObject json, String key) {
        try {
            if (json.has(key)) {
                JsonElement element = json.get(key);
                if (element.isJsonArray()) {
                    return element.getAsJsonArray();
                }
            }
        } catch (Exception e) {
            // Silently handle and return null
        }
        return null;
    }
    
    /**
     * Safely extract an integer from a JSON object.
     * 
     * @param json The JSON object.
     * @param key The key to extract.
     * @param defaultValue The default value if extraction fails.
     * @return The integer value, or the default value if not found or not an integer.
     */
    private int getIntFromJson(JsonObject json, String key, int defaultValue) {
        try {
            if (json.has(key)) {
                JsonElement element = json.get(key);
                if (element.isJsonPrimitive()) {
                    JsonPrimitive primitive = element.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        return primitive.getAsInt();
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle and return default
        }
        return defaultValue;
    }
}
