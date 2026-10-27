package stockholm.order;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** An order to bring the requested item into the inventory that holds it. */
public class ImportOrder extends Order {
    /** Creates an import order destined for the current inventory; stock is unchanged until delivery. */
    public ImportOrder(Item item, Inventory currInventory) {
        this(item, currInventory, OrderState.WAITING_APPROVAL);
    }

    /** Restores an import order with its saved state and optional rejection reason; stock is unchanged. */
    public ImportOrder(Item item, Inventory currInventory, OrderState state, String reason) {
        super(item, state, reason);
        setDst(currInventory.getName());
    }

    /** Restores an import order with its saved state; stock is unchanged. */
    public ImportOrder(Item item, Inventory currInventory, OrderState state) {
        this(item, currInventory, state, null);
    }

    @Override
    public String getKindLabel() {
        return "Import order";
    }
}
