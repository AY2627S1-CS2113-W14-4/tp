package stockholm.order;

import stockholm.inventory.Item;

/** An order's item and its current approval state. */
public class Order {
    String dateCreated; // TODO: implement DateTime system for this
    String notes; // TODO: implement optional notes field
    private OrderState state;
    private final Item item;
    private String src;
    private String dst;

    /** Creates an order awaiting approval for the given item. */
    public Order(Item item) {
        this.state = OrderState.WAITING_APPROVAL;
        this.item = item;
    }

    public OrderState getState() {
        return state;
    }

    public Item getItem() {
        return item;
    }

    public void setSrc(String srcInventory) {
        src = srcInventory;
    }

    public void setDst(String dstInventory) {
        dst = dstInventory;
    }
}
