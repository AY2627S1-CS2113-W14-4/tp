package stockholm.order;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** An order to bring the requested item into the inventory that holds it. */
public class ImportOrder extends Order {
    /** Creates an import order destined for the current inventory; stock is unchanged until delivery. */
    public ImportOrder(Item item, Inventory currInventory) {
        super(item);
        setDst(currInventory.getName());
    }
}
