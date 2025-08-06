package eu.venxu.mineshopify.storage;

import eu.venxu.mineshopify.order.Order;

public interface IStorage {

    void createConnection();

    void addOrder(Order order);

    boolean checkOrder(String orderId);
}
