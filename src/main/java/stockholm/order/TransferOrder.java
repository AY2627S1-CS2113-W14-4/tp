package stockholm.order;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** An order moving reserved stock from one inventory to another on delivery. */
public class TransferOrder extends Order {
    /** Creates a transfer awaiting approval; the Processor reserves its stock. */
    public TransferOrder(Item item, Inventory source, Inventory destination) {
        this(item, source, destination, OrderState.WAITING_APPROVAL);
    }

    /** Restores a transfer without replaying stock changes. */
    public TransferOrder(Item item, Inventory source, Inventory destination, OrderState state) {
        this(item, source, destination, state, null);
    }

    /** Restores a transfer's saved state and optional rejection reason. */
    public TransferOrder(Item item, Inventory source, Inventory destination, OrderState state, String reason) {
        super(item, state, reason);
        setSrc(source.getName());
        setDst(destination.getName());
    }

    /** Returns the source inventory, used as the owning inventory in the saved CSV. */
    @Override
    public String getInventoryName() {
        return getSrc();
    }

    @Override
    public String getKindLabel() {
        return "Transfer order";
    }
}
