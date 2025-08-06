package eu.venxu.mineshopify.order;

import java.util.UUID;

public class Order {

    private final UUID id;
    private final String username;
    private final String packageName;
    private final String orderId;

    public Order(String username, String packageName, String orderId) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.packageName = packageName;
        this.orderId = orderId;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getOrderId() {
        return orderId;
    }
}
