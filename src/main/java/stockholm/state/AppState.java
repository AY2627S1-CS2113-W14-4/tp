package stockholm.state;

import java.util.ArrayList;

import stockholm.inventory.Inventory;
import stockholm.order.OrderHistory;

/**
 * Everything the app remembers between commands: all inventories, the history of all orders, and which
 * inventory the user is currently inside (via {@code enter}), if any.
 *
 * <p>{@code StockHolm} creates one instance and passes it to the Processor for every command,
 * so the Processor itself can stay stateless.
 */
public class AppState {
    private final ArrayList<Inventory> inventories = new ArrayList<>();
    private final OrderHistory orderHistory = new OrderHistory();
    /** The inventory the user has entered, or {@code null} when not inside any inventory. */
    private Inventory currentInventory;

    /** Returns the list of all inventories; callers may modify it. */
    public ArrayList<Inventory> getInventories() {
        return inventories;
    }

    /** Returns every order across all inventories, including orders of deleted inventories. */
    public OrderHistory getOrderHistory() {
        return orderHistory;
    }

    /**
     * Returns the inventory with the given name, or {@code null} if there is none.
     * Names are compared case-sensitively.
     *
     * @param name the name to look for
     * @return the matching inventory, or {@code null}
     */
    public Inventory findInventory(String name) {
        for (Inventory inventory : inventories) {
            if (inventory.getName().equals(name)) {
                return inventory;
            }
        }
        return null;
    }

    /**
     * Returns the inventory the user is inside.
     *
     * @return the current inventory, or {@code null} if the user is not inside one
     */
    public Inventory getCurrentInventory() {
        return currentInventory;
    }

    public boolean isInsideInventory() {
        return currentInventory != null;
    }

    /**
     * Makes {@code inventory} the current one. Entering while already inside another inventory
     * switches directly to the new one.
     *
     * @param inventory the inventory to enter; should be one of {@link #getInventories()}
     */
    public void enterInventory(Inventory inventory) {
        currentInventory = inventory;
    }

    /** Leaves the current inventory, returning to the top level. */
    public void leaveInventory() {
        currentInventory = null;
    }
}
