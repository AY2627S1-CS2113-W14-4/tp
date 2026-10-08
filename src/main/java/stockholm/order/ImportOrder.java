package stockholm.order;

import stockholm.inventory.Item;

/** An order to bring the requested item into the inventory that holds it. */
public class ImportOrder extends Order {
    /** Creates an import order awaiting approval; stock is unchanged until delivery. */
    public ImportOrder(Item item) {
        super(item);
    }
}
