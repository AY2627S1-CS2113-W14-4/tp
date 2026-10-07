package stockholm.inventory;

import java.util.ArrayList;

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
}
