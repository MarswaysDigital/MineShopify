package eu.venxu.mineshopify.storage;

import eu.venxu.mineshopify.MineShopify;

public class StorageManager {

    private final MineShopify mineShopify;
    private IStorage storage;

    /**
     * Set which type of storage is choosen.
     *
     * @param mineShopify The main class.
     */
    public StorageManager(MineShopify mineShopify) {
        this.mineShopify = mineShopify;
        if(mineShopify.getConfig().getBoolean("storage.MySQL")) storage = new MySQLStorage(mineShopify);
        else storage = new FileStorage(mineShopify);
    }

    /**
     * Change the storage to FileStorage.
     * This will be executed when MySQL couldn't connect.
     */
    public void changeStorage() {
        if(storage instanceof MySQLStorage)
            storage = new FileStorage(mineShopify);
    }

    /**
     * Get the storage of the plugin.
     *
     * @return The storage of the plugin.
     */
    public IStorage getStorage() {
        return storage;
    }
}
