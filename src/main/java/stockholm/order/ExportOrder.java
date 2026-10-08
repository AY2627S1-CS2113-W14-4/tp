package stockholm.order;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** An order recording stock exported from the inventory that holds it. */
public class ExportOrder extends Order {
    /** Creates an export order from the current inventory with a separate record of the exported item. */
    public ExportOrder(Item item, Inventory currInventory) {
        super(item);
        setSrc(currInventory.getName());
    }
}
