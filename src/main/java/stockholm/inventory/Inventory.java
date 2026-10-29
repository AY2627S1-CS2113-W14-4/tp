package stockholm.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.exceptions.StockHolmException;

/**
 * A named collection of {@link Item}s, e.g. one warehouse or shop.
 */
public class Inventory {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(Inventory.class.getName());
    private final String name;
    private final ArrayList<Item> items = new ArrayList<>();

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
     * Returns whether {@code item} can be added by {@link #addOrMerge}: either no item has its name, or the
     * existing item has the same type. An empty type matches any type, so it never conflicts.
     * Call this to reject a command before changing anything else.
     *
     * @param item the item about to be added
     * @return {@code false} if an item with the same name has a different type
     */
    public boolean checkCanMerge(Item item) {
        assert item != null : "Cannot check a null item";
        Item existing = findItem(item.getName());
        boolean isNewName = existing == null;
        boolean hasNoType = item.getType().isEmpty();
        boolean isSameType = !isNewName && item.getType().equals(existing.getType());
        return isNewName || hasNoType || isSameType;
    }

    /**
     * Adds an item, or merges it into the existing item with the same name by adding its count.
     * Nothing changes if the merge is rejected.
     *
     * @param item the item to add; when merging, only its count is used
     * @return the existing item it was merged into, or {@code item} itself if it was added as a new item
     * @throws StockHolmException if an item with the same name has a different type (see {@link #checkCanMerge})
     */
    public Item addOrMerge(Item item) throws StockHolmException {
        Item existing = findItem(item.getName());
        if (!checkCanMerge(item)) {
            LOGGER.log(Level.FINE, "Rejected merging {0} into {1}: type differs", new Object[]{item, name});
            throw new StockHolmException("Item already exists with a different type: " + existing + ".");
        }
        if (existing == null) {
            addItem(item);
            return item;
        }
        existing.addCount(item.getCount());
        LOGGER.log(Level.FINE, "Merged {0} into {1}", new Object[]{item.getName(), name});
        return existing;
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
