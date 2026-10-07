package stockholm.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named collection of {@link Item}s, e.g. one warehouse or shop.
 */
public class Inventory {
    private final String name;
    private final ArrayList<Item> items = new ArrayList<>();

    /**
     * Creates an empty inventory.
     *
     * @param name display name, also used to look the inventory up
     */
    public Inventory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addItem(Item item) {
        items.add(item);
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
        return items.remove(index);
    }
}
