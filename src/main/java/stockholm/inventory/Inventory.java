package stockholm.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.order.Order;

/**
 * A named collection of {@link Item}s, e.g. one warehouse or shop.
 */
public class Inventory {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(Inventory.class.getName());
    private final String name;
    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Order> orders = new ArrayList<>();

    /**
     * Creates an empty inventory.
     *
     * @param name display name, also used to look the inventory up
     */
    public Inventory(String name) {
        assert name != null && !name.isBlank() : "An inventory must have a non-blank name";
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addItem(Item item) {
        assert item != null : "Cannot add a null item";
        items.add(item);
        LOGGER.log(Level.FINE, "Added {0} to {1}", new Object[]{item.getName(), name});
    }

    /**
     * Returns the items in the order they were added.
     * The list is read-only; use {@link #addItem} and {@link #removeItem} to change it.
     *
     * @return an unmodifiable view of the items
     */
    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** Stores an order for this inventory without changing its stock. */
    public void addOrder(Order order) {
        assert order != null : "Cannot add a null order";
        orders.add(order);
        LOGGER.log(Level.FINE, "Added {0} to {1}", new Object[]{order.getClass().getSimpleName(), name});
    }

    /** Returns a read-only view of orders in the order they were created. */
    public List<Order> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    /**
     * Returns the item with the given name, or {@code null} if there is none.
     * Names are compared case-sensitively, like inventory names.
     *
     * @param itemName the name to look for
     * @return the matching item, or {@code null}
     */
    public Item findItem(String itemName) {
        assert itemName != null : "Item name to find must not be null";
        for (Item item : items) {
            if (item.getName().equals(itemName)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Removes and returns the item at a zero-based position.
     *
     * @param index zero-based position in {@link #getItems()}
     * @return the removed item
     * @throws IndexOutOfBoundsException if there is no item at {@code index}
     */
    public Item removeItem(int index) {
        Item removed = items.remove(index);
        LOGGER.log(Level.FINE, "Removed {0} from {1}", new Object[]{removed.getName(), name});
        return removed;
    }
}
