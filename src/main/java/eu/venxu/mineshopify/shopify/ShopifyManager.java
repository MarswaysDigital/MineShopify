package eu.venxu.mineshopify.shopify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.venxu.mineshopify.MineShopify;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ShopifyManager {

    private JsonObject response;
    private final HttpClient httpClient;
    private final MineShopify mineShopify;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Initialize the Shopify Manager with an optimized HTTP client
     * and schedule regular order fetching.
     * 
     * @param mineShopify The main plugin instance.
     */
    public ShopifyManager(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        
        // Create a reusable HttpClient with optimized settings
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Schedule the order fetching task
        scheduleOrderFetching(mineShopify);
    }
    
    /**
     * Schedule the periodic order fetching task.
     * 
     * @param mineShopify The main plugin instance.
     */
    private void scheduleOrderFetching(MineShopify mineShopify) {
        new BukkitRunnable() {
            @Override
            public void run() {
                fetchOrders();
            }
        }.runTaskTimerAsynchronously(mineShopify, 0, mineShopify.getConfig().getInt("shopify.scheduler") * 20L);
    }
    
    /**
     * Fetch orders from Shopify API asynchronously using modern HttpClient.
     */
    private void fetchOrders() {
        try {
            String domain = mineShopify.getConfig().getString("shopify.domain");
            String token = mineShopify.getConfig().getString("shopify.token");
            
            if (domain == null || token == null || domain.isEmpty() || token.isEmpty()) {
                mineShopify.getLogger().warning("Shopify domain or token not configured properly.");
                return;
            }
            
            // Get configuration values
            int daysToCheck = mineShopify.getConfig().getInt("shopify.days_to_check", 1);
            int maxOrders = mineShopify.getConfig().getInt("shopify.max_orders", 50);
            
            // Calculate date based on days_to_check
            LocalDate checkDate = LocalDate.now().minusDays(daysToCheck - 1);
            String formattedDate = checkDate.format(dateFormatter);
            
            // Build the API URL with limit parameter
            String apiUrl = String.format("https://%s/admin/api/2023-10/orders.json?status=any&created_at_min=%s&limit=%d", 
                    domain, formattedDate, maxOrders);
            
            // Create and send the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Shopify-Access-Token", token)
                    .GET()
                    .build();
            
            // Log the request URL (debug level)
            if (mineShopify.getConfig().getBoolean("debug", false)) {
                mineShopify.getLogger().info("Fetching orders from: " + apiUrl);
            }
            
            // Send the request asynchronously
            CompletableFuture<HttpResponse<String>> responseFuture = 
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            // Process the response when it completes
            responseFuture.thenAccept(httpResponse -> {
                if (httpResponse.statusCode() == 200) {
                    String responseBody = httpResponse.body();
                    
                    // Parse the JSON response
                    try {
                        // Always log the raw response for debugging
                        mineShopify.getLogger().info("Shopify API response received with status 200");
                        mineShopify.getLogger().info("Response length: " + responseBody.length() + " characters");
                        mineShopify.getLogger().info("Response preview: " + responseBody.substring(0, Math.min(500, responseBody.length())) + "...");
                        
                        // Save response to file for debugging
                        try {
                            java.nio.file.Files.writeString(java.nio.file.Paths.get("plugins/MineShopify/debug_response.json"), responseBody);
                            mineShopify.getLogger().info("Saved full API response to plugins/MineShopify/debug_response.json");
                        } catch (Exception fileEx) {
                            mineShopify.getLogger().warning("Could not save debug response: " + fileEx.getMessage());
                        }
                        
                        JsonParser parser = new JsonParser();
                        response = parser.parse(responseBody).getAsJsonObject();
                        
                        // Log the parsed response structure
                        mineShopify.getLogger().info("Parsed response has these root keys: " + response.keySet());
                        
                        // Check if orders array exists and how many orders it contains
                        if (response.has("orders")) {
                            JsonArray orders = response.getAsJsonArray("orders");
                            mineShopify.getLogger().info("Found " + orders.size() + " orders in response");
                            
                            // Log first order structure if available
                            if (orders.size() > 0) {
                                JsonObject firstOrder = orders.get(0).getAsJsonObject();
                                mineShopify.getLogger().info("First order has these keys: " + firstOrder.keySet());
                                
                                // Detaillierte Analyse der ersten Bestellung
                                if (mineShopify.getConfig().getBoolean("debug", false)) {
                                    // Prüfe auf line_items und deren Struktur
                                    if (firstOrder.has("line_items")) {
                                        JsonArray lineItems = firstOrder.getAsJsonArray("line_items");
                                        mineShopify.getLogger().info("First order has " + lineItems.size() + " line items");
                                        
                                        if (lineItems.size() > 0) {
                                            JsonObject firstLineItem = lineItems.get(0).getAsJsonObject();
                                            mineShopify.getLogger().info("First line item has these keys: " + firstLineItem.keySet());
                                            
                                            // Prüfe auf properties
                                            if (firstLineItem.has("properties")) {
                                                JsonArray properties = firstLineItem.getAsJsonArray("properties");
                                                mineShopify.getLogger().info("First line item has " + properties.size() + " properties");
                                                
                                                // Gib alle Properties aus
                                                for (int i = 0; i < properties.size(); i++) {
                                                    JsonObject property = properties.get(i).getAsJsonObject();
                                                    String name = property.has("name") ? property.get("name").getAsString() : "null";
                                                    String value = property.has("value") ? property.get("value").getAsString() : "null";
                                                    mineShopify.getLogger().info("Property " + i + ": name=" + name + ", value=" + value);
                                                }
                                            } else {
                                                mineShopify.getLogger().info("First line item has no properties array");
                                            }
                                        }
                                    }
                                    
                                    // Prüfe auf note_attributes
                                    if (firstOrder.has("note_attributes")) {
                                        JsonArray noteAttributes = firstOrder.getAsJsonArray("note_attributes");
                                        mineShopify.getLogger().info("First order has " + noteAttributes.size() + " note attributes");
                                        
                                        // Gib alle Note Attributes aus
                                        for (int i = 0; i < noteAttributes.size(); i++) {
                                            JsonObject attribute = noteAttributes.get(i).getAsJsonObject();
                                            String name = attribute.has("name") ? attribute.get("name").getAsString() : "null";
                                            String value = attribute.has("value") ? attribute.get("value").getAsString() : "null";
                                            mineShopify.getLogger().info("Note attribute " + i + ": name=" + name + ", value=" + value);
                                        }
                                    } else {
                                        mineShopify.getLogger().info("First order has no note_attributes array");
                                    }
                                    
                                    // Prüfe auf customer
                                    if (firstOrder.has("customer")) {
                                        JsonObject customer = firstOrder.getAsJsonObject("customer");
                                        mineShopify.getLogger().info("Customer object has these keys: " + customer.keySet());
                                    }
                                }
                            }
                        } else {
                            mineShopify.getLogger().warning("No 'orders' array found in response!");
                        }
                        
                        // Process orders on the main thread
                        Bukkit.getScheduler().runTask(mineShopify, () -> 
                            mineShopify.getParseManager().parseOrders());
                    } catch (Exception e) {
                        mineShopify.getLogger().log(Level.SEVERE, "Failed to parse Shopify API response", e);
                    }
                } else {
                    mineShopify.getLogger().warning("Shopify API returned status code: " + httpResponse.statusCode());
                }
            }).exceptionally(e -> {
                mineShopify.getLogger().log(Level.SEVERE, "Failed to fetch orders from Shopify API", e);
                return null;
            });
            
        } catch (Exception e) {
            mineShopify.getLogger().log(Level.SEVERE, "Error in Shopify order fetching", e);
        }
    }

    /**
     * Get the response with the orders' data.
     *
     * @return The orders from Shopify as a JSON object.
     */
    public JsonObject getResponse() {
        return response;
    }
}
