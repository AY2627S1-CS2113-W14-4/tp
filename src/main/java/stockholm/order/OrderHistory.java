package stockholm.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every order ever created, across all inventories, in the order they were created.
 * Orders are kept here rather than inside an {@code Inventory}, so they outlive the inventory they name:
 * each order records its inventory by name in {@link Order#getSrc()} or {@link Order#getDst()}.
 */
public class OrderHistory {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(OrderHistory.class.getName());
    private final ArrayList<Order> orders = new ArrayList<>();

    /** Records a new or restored order after all existing ones; stock is unchanged. */
    public void add(Order order) {
        assert order != null : "Cannot add a null order";
        orders.add(order);
        LOGGER.log(Level.FINE, "Added {0} for {1}",
                new Object[]{order.getClass().getSimpleName(), order.getInventoryName()});
    }

    /** Returns a read-only view of all orders in the order they were created. */
    public List<Order> getOrders() {
        return Collections.unmodifiableList(orders);
    }
}
