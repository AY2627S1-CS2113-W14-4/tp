package stockholm.order;

import java.util.Objects;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Item;

/** An order's item and its current approval state. */
public class Order {
    private String dateCreated; // TODO: implement DateTime system for this
    private String notes; // TODO: implement optional notes field
    private OrderState state;
    private final Item item;
    private String src;
    private String dst;

    /** Creates an order awaiting approval for the given item. */
    public Order(Item item) {
        this(item, OrderState.WAITING_APPROVAL);
    }

    /** Restores an order's recorded state without applying stock changes or replaying transitions. */
    public Order(Item item, OrderState state) {
        this.state = Objects.requireNonNull(state);
        this.item = Objects.requireNonNull(item);
    }

    public OrderState getState() {
        return state;
    }

    /** Approves an order only while it is waiting for approval. */
    public void approve() throws StockHolmException {
        if (state != OrderState.WAITING_APPROVAL) {
            throw new StockHolmException("Order is not waiting for approval (current state: " + state + ").");
        }
        state = OrderState.WAITING_FOR_DELIVERY;
    }

    /** Marks an order as delivered only while it is waiting for delivery. */
    public void deliver() throws StockHolmException {
        if (state != OrderState.WAITING_FOR_DELIVERY) {
            throw new StockHolmException("Order is not waiting for delivery (current state: " + state + ").");
        }
        state = OrderState.DELIVERED;
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

    public String getSrc() {
        return src;
    }

    public String getDst() {
        return dst;
    }
}
