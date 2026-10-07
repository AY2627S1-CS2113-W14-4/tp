package stockholm.state;

import java.util.ArrayList;

import stockholm.inventory.Inventory;

/**
 * Everything the app remembers between commands: all inventories, and which inventory
 * the user is currently inside (via {@code enter}), if any.
 *
 * <p>{@code StockHolm} creates one instance and passes it to the Processor for every command,
 * so the Processor itself can stay stateless.
 */
public class AppState {
    private final ArrayList<Inventory> inventories = new ArrayList<>();
    /** The inventory the user has entered, or {@code null} when not inside any inventory. */
    private Inventory currentInventory;

    /** Returns the list of all inventories; callers may modify it. */
    public ArrayList<Inventory> getInventories() {
        return inventories;
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
