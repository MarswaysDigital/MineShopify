package eu.venxu.mineshopify.commands;

import eu.venxu.mineshopify.MineShopify;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles commands for the MineShopify plugin.
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final MineShopify plugin;

    public CommandHandler(MineShopify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getNotificationManager().loadConfig();
                sender.sendMessage(MineShopify.PREFIX + "&aKonfiguration wurde neu geladen! 🔄");
                return true;
                
            case "status":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                showStatus(sender);
                return true;
                
            case "convertproduct":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("&c🚫 Dieser Befehl kann nur von Spielern ausgeführt werden!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(MineShopify.PREFIX + "&e📝 Bitte gib einen Produktnamen an: &f/mineshopify convertproduct <Produktname>");
                    return true;
                }
                
                // Extract product name from args (may contain spaces)
                StringBuilder productName = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) {
                    productName.append(" ").append(args[i]);
                }
                
                convertProduct((Player) sender, productName.toString());
                return true;
                
            case "addcommand":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("&c🚫 Dieser Befehl kann nur von Spielern ausgeführt werden!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(MineShopify.PREFIX + "&e➕ Bitte gib einen Produktnamen und Befehl an: &f/mineshopify addcommand <Produktname> <Befehl>");
                    return true;
                }
                
                // Extract product name and command
                String cmdProductName = args[1];
                StringBuilder commandStr = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) commandStr.append(" ");
                    commandStr.append(args[i]);
                }
                
                addCommandToProduct((Player) sender, cmdProductName, commandStr.toString());
                return true;
                
            case "listproducts":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                listProducts(sender);
                return true;
                
            case "removecommand":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("&c🚫 Dieser Befehl kann nur von Spielern ausgeführt werden!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(MineShopify.PREFIX + "Bitte gib einen Produktnamen und Befehlsindex an: /mineshopify removecommand <Produktname> <Index>");
                    return true;
                }
                
                // Extract product name and command index
                String removeProductName = args[1];
                int commandIndex;
                try {
                    commandIndex = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Der Index muss eine Zahl sein.");
                    return true;
                }
                
                removeCommandFromProduct((Player) sender, removeProductName, commandIndex);
                return true;
                
            case "deleteproduct":
                if (!sender.hasPermission("mineshopify.admin")) {
                    sender.sendMessage("&c❌ Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("&c🚫 Dieser Befehl kann nur von Spielern ausgeführt werden!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(MineShopify.PREFIX + "Bitte gib einen Produktnamen an: /mineshopify deleteproduct <Produktname>");
                    return true;
                }
                
                // Extract product name
                String deleteProductName = args[1];
                deleteProduct((Player) sender, deleteProductName);
                return true;
                
            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MineShopify Hilfe ===");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify reload " + ChatColor.GRAY + "- Lädt die Konfiguration neu");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify status " + ChatColor.GRAY + "- Zeigt den Status des Plugins");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify convertproduct <Produktname> " + ChatColor.GRAY + "- Konvertiert ein Produkt in das config.yml Format");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify addcommand <Produktname> <Befehl> " + ChatColor.GRAY + "- Fügt einen Befehl zu einem Produkt hinzu");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify removecommand <Produktname> <Index> " + ChatColor.GRAY + "- Entfernt einen Befehl von einem Produkt");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify deleteproduct <Produktname> " + ChatColor.GRAY + "- Löscht ein Produkt aus der Konfiguration");
        sender.sendMessage(ChatColor.YELLOW + "/mineshopify listproducts " + ChatColor.GRAY + "- Listet alle konfigurierten Produkte auf");
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== MineShopify Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        
        // Shopify Status
        String domain = plugin.getConfig().getString("shopify.domain", "Nicht konfiguriert");
        boolean hasToken = plugin.getConfig().getString("shopify.token", "").length() > 10;
        sender.sendMessage(ChatColor.YELLOW + "Shopify Domain: " + ChatColor.WHITE + domain);
        sender.sendMessage(ChatColor.YELLOW + "API Token: " + (hasToken ? ChatColor.GREEN + "Konfiguriert" : ChatColor.RED + "Nicht konfiguriert"));
        
        // Storage Status
        boolean usingMySQL = plugin.getConfig().getBoolean("storage.MySQL", false);
        sender.sendMessage(ChatColor.YELLOW + "Speicher-Typ: " + ChatColor.WHITE + (usingMySQL ? "MySQL" : "Datei"));
        
        // Debug Status
        boolean debugEnabled = plugin.getConfig().getBoolean("debug", false);
        sender.sendMessage(ChatColor.YELLOW + "Debug-Modus: " + (debugEnabled ? ChatColor.GREEN + "Aktiviert" : ChatColor.RED + "Deaktiviert"));
        
        // Notification Status
        boolean notificationsEnabled = plugin.getConfig().getBoolean("notifications.enabled", true);
        sender.sendMessage(ChatColor.YELLOW + "Benachrichtigungen: " + (notificationsEnabled ? ChatColor.GREEN + "Aktiviert" : ChatColor.RED + "Deaktiviert"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "status", "convertproduct", "addcommand", "removecommand", "deleteproduct", "listproducts");
            return completions.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("convertproduct") || 
                                       args[0].equalsIgnoreCase("addcommand") || 
                                       args[0].equalsIgnoreCase("removecommand") ||
                                       args[0].equalsIgnoreCase("deleteproduct"))) {
            // Get all package names from config for tab completion
            if (plugin.getConfig().contains("packages")) {
                return plugin.getConfig().getConfigurationSection("packages").getKeys(false).stream()
                        .filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("removecommand")) {
            // Get command indices for the specified product
            String productName = args[1];
            if (plugin.getConfig().contains("packages." + productName)) {
                List<String> commands = plugin.getConfig().getStringList("packages." + productName + ".commands");
                List<String> indices = new ArrayList<>();
                for (int i = 0; i < commands.size(); i++) {
                    indices.add(String.valueOf(i));
                }
                return indices.stream()
                        .filter(c -> c.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
    
    /**
     * Converts a product to the config.yml format and shows the result to the player.
     * 
     * @param player The player executing the command
     * @param productName The name of the product to convert
     */
    private void convertProduct(Player player, String productName) {
        player.sendMessage(MineShopify.PREFIX + "Konvertiere Produkt: " + ChatColor.YELLOW + productName);
        
        // Check if the product already exists in the configuration
        boolean productExists = false;
        List<String> existingCommands = new ArrayList<>();
        
        if (plugin.getConfig().contains("packages." + productName)) {
            productExists = true;
            existingCommands = plugin.getConfig().getStringList("packages." + productName + ".commands");
            player.sendMessage(ChatColor.YELLOW + "Produkt existiert bereits in der Konfiguration.");
        }
        
        // Generate YAML configuration for the product
        StringBuilder yamlConfig = new StringBuilder();
        yamlConfig.append("\"" + productName + "\":\n");
        yamlConfig.append("  commands:\n");
        
        // Add existing commands if available, otherwise add example commands
        if (!existingCommands.isEmpty()) {
            for (String cmd : existingCommands) {
                yamlConfig.append("    - \"" + cmd + "\"\n");
            }
        } else {
            yamlConfig.append("    - \"lp user %player% group add groupname\"\n");
            yamlConfig.append("    - \"give %player% diamond 10\"\n");
            yamlConfig.append("    - \"broadcast %player% hat " + productName + " gekauft!\"\n");
        }
        
        // Show the YAML configuration to the player
        player.sendMessage(ChatColor.GOLD + "=== Produkt Konfiguration ===");
        player.sendMessage(ChatColor.GRAY + "Füge folgendes in deine config.yml unter dem 'packages:' Abschnitt ein:");
        player.sendMessage(ChatColor.WHITE + yamlConfig.toString());
        
        // Show additional instructions
        if (productExists) {
            player.sendMessage(ChatColor.GRAY + "Das Produkt existiert bereits in der Konfiguration. Die obigen Befehle sind die aktuellen Befehle.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Denke daran, die Befehle nach deinen Bedürfnissen anzupassen.");
        }
        
        // Show command to add a custom command
        player.sendMessage(ChatColor.YELLOW + "Verwende /mineshopify addcommand " + productName + " <Befehl> um einen Befehl hinzuzufügen.");
    }
    
    /**
     * Adds a command to a product in the config.yml.
     * 
     * @param player The player executing the command
     * @param productName The name of the product
     * @param command The command to add
     */
    private void addCommandToProduct(Player player, String productName, String command) {
        player.sendMessage(MineShopify.PREFIX + "Füge Befehl zu Produkt hinzu: " + ChatColor.YELLOW + productName);
        
        // Check if the product exists in the configuration
        if (!plugin.getConfig().contains("packages." + productName)) {
            // Create new product with this command
            List<String> commands = new ArrayList<>();
            commands.add(command);
            plugin.getConfig().set("packages." + productName + ".commands", commands);
            plugin.saveConfig();
            
            player.sendMessage(ChatColor.GREEN + "Neues Produkt '" + productName + "' mit Befehl erstellt: " + command);
        } else {
            // Add command to existing product
            List<String> commands = plugin.getConfig().getStringList("packages." + productName + ".commands");
            commands.add(command);
            plugin.getConfig().set("packages." + productName + ".commands", commands);
            plugin.saveConfig();
            
            player.sendMessage(ChatColor.GREEN + "Befehl zu Produkt '" + productName + "' hinzugefügt: " + command);
        }
        
        // Show the updated product configuration
        convertProduct(player, productName);
    }
    
    /**
     * Lists all products configured in the config.yml file.
     * 
     * @param sender The command sender
     */
    private void listProducts(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Konfigurierte Produkte ===");
        
        if (!plugin.getConfig().contains("packages") || plugin.getConfig().getConfigurationSection("packages").getKeys(false).isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Keine Produkte konfiguriert.");
            return;
        }
        
        // Get all products from config
        for (String productName : plugin.getConfig().getConfigurationSection("packages").getKeys(false)) {
            List<String> commands = plugin.getConfig().getStringList("packages." + productName + ".commands");
            sender.sendMessage(ChatColor.YELLOW + productName + ChatColor.GRAY + " (§f" + commands.size() + "§7 Befehle)");
            
            // Always show commands with indices
            for (int i = 0; i < commands.size(); i++) {
                sender.sendMessage(ChatColor.GRAY + "  [" + i + "] §f" + commands.get(i));
            }
        }
        
        // Show how to view product details
        sender.sendMessage(ChatColor.GRAY + "Verwende /mineshopify convertproduct <Produktname> um Details zu einem Produkt anzuzeigen.");
        sender.sendMessage(ChatColor.GRAY + "Verwende /mineshopify removecommand <Produktname> <Index> um einen Befehl zu entfernen.");
    }
    
    /**
     * Removes a command from a product in the config.yml.
     * 
     * @param player The player executing the command
     * @param productName The name of the product
     * @param commandIndex The index of the command to remove
     */
    private void removeCommandFromProduct(Player player, String productName, int commandIndex) {
        player.sendMessage(MineShopify.PREFIX + "Entferne Befehl von Produkt: " + ChatColor.YELLOW + productName);
        
        // Check if the product exists in the configuration
        if (!plugin.getConfig().contains("packages." + productName)) {
            player.sendMessage(ChatColor.RED + "Produkt '" + productName + "' existiert nicht in der Konfiguration.");
            return;
        }
        
        // Get commands for the product
        List<String> commands = plugin.getConfig().getStringList("packages." + productName + ".commands");
        
        // Check if the command index is valid
        if (commandIndex < 0 || commandIndex >= commands.size()) {
            player.sendMessage(ChatColor.RED + "Ungültiger Befehlsindex. Der Index muss zwischen 0 und " + (commands.size() - 1) + " liegen.");
            return;
        }
        
        // Remove the command
        String removedCommand = commands.get(commandIndex);
        commands.remove(commandIndex);
        
        // Update the config
        plugin.getConfig().set("packages." + productName + ".commands", commands);
        plugin.saveConfig();
        
        player.sendMessage(ChatColor.GREEN + "Befehl von Produkt '" + productName + "' entfernt: " + removedCommand);
        
        // Show the updated product configuration
        convertProduct(player, productName);
    }
    
    /**
     * Deletes a product from the config.yml.
     * 
     * @param player The player executing the command
     * @param productName The name of the product to delete
     */
    private void deleteProduct(Player player, String productName) {
        player.sendMessage(MineShopify.PREFIX + "Lösche Produkt: " + ChatColor.YELLOW + productName);
        
        // Check if the product exists in the configuration
        if (!plugin.getConfig().contains("packages." + productName)) {
            player.sendMessage(ChatColor.RED + "Produkt '" + productName + "' existiert nicht in der Konfiguration.");
            return;
        }
        
        // Get commands for the product to show what will be deleted
        List<String> commands = plugin.getConfig().getStringList("packages." + productName + ".commands");
        player.sendMessage(ChatColor.YELLOW + "Das Produkt hat " + commands.size() + " Befehle, die gelöscht werden.");
        
        // Delete the product
        plugin.getConfig().set("packages." + productName, null);
        plugin.saveConfig();
        
        player.sendMessage(ChatColor.GREEN + "Produkt '" + productName + "' wurde erfolgreich aus der Konfiguration gelöscht.");
        
        // Show updated product list
        listProducts(player);
    }
}
