# 🛒 MineShopify - Minecraft Shopify Integration Plugin

A universal Minecraft plugin that connects Shopify with your Minecraft server and automatically processes orders.

## ✨ Features

- **Automatic Order Processing**: Synchronizes Shopify orders with your Minecraft server
- **Flexible Package System**: Configurable commands for different products
- **Multi-Storage Support**: MySQL or file-based storage
- **Notification System**: In-game notifications for administrators
- **Debug Mode**: Detailed logging for troubleshooting
- **Generic Design**: Works with any Minecraft server and Shopify store

## 🚀 Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins/` folder
3. Restart your server
4. Configure the `config.yml` with your Shopify credentials
5. Restart the server again

## ⚙️ Configuration

### Shopify API Setup

1. Go to your Shopify Admin Panel
2. Navigate to "Apps" → "Develop apps"
3. Create a new app
4. Enable the following permissions:
   - `read_orders` (required)
   - `read_customers` (recommended)
   - `read_products` (optional)
5. Copy the API token to your `config.yml`

### Package Configuration

```yaml
packages:
  "VIP Rank":
    commands:
      - "lp user %player% group add vip"
      - "give %player% diamond 32"
      - "title %player% title {\"text\":\"VIP\",\"color\":\"gold\",\"bold\":true}"
```

**Important**: The package name must match exactly with the product name in Shopify!

## 🎮 Commands

- `/mineshopify reload` - Reloads the configuration
- `/mineshopify status` - Shows plugin status

**Permission**: `mineshopify.admin` (Default: OP)

## 🔧 Advanced Configuration

### MySQL Storage

```yaml
storage:
  MySQL: true
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "user"
  password: "password"
```

### Notifications

```yaml
notifications:
  enabled: true
  recipients:
    - "admin"
    - "owner"
  message: "&6[&e&lSHOP&6] &e%player% &7purchased &6%package%&7! 🎉"
```

## 🛠️ Development

The plugin is built with a modular architecture:
- `ShopifyManager` - API communication
- `StorageManager` - Data persistence
- `ParseManager` - JSON processing
- `NotificationManager` - Notifications

## 📝 Placeholders

Available placeholders in commands and messages:
- `%player%` - Player name
- `%package%` - Package name
- `%order_id%` - Shopify order ID

## 🐛 Troubleshooting

1. **Plugin won't load**: Check Java version (minimum 8)
2. **No orders processed**: Verify API token and permissions
3. **Commands not working**: Ensure required plugins (LuckPerms, Economy) are installed

Enable debug mode for detailed logs:
```yaml
debug: true
```

## 📋 Requirements

- **Minecraft**: 1.13+
- **Java**: 8+
- **Dependencies**: None (optional: LuckPerms, Economy plugin)


## 📄 License

Copyright © 2025 MineShopify by Marsways Digital Services

## 🤝 Support

- Website: https://mineshopify.com
- Documentation: https://docs.mineshopify.com

---

**Note**: This plugin is generic and can be used with any Minecraft Spigot / Paper server. Customize the configuration to fit your needs.
