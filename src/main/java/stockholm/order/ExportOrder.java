package stockholm.order;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** An order recording stock exported from the inventory that holds it. */
public class ExportOrder extends Order {
    /** Creates an export order from the current inventory with a separate record of the exported item. */
    public ExportOrder(Item item, Inventory currInventory) {
        this(item, currInventory, OrderState.WAITING_APPROVAL);
    }

    /** Restores an export order with its saved state and optional rejection reason; stock is unchanged. */
    public ExportOrder(Item item, Inventory currInventory, OrderState state, String reason) {
        super(item, state, reason);
        setSrc(currInventory.getName());
    }

    /** Restores an export order with its saved state; stock is unchanged. */
    public ExportOrder(Item item, Inventory currInventory, OrderState state) {
        this(item, currInventory, state, null);
    }

    /** Returns the source inventory, whose stock was reduced when the order was created. */
    @Override
    public String getInventoryName() {
        return getSrc();
    }

    @Override
    public String getKindLabel() {
        return "Export order";
    }
}
