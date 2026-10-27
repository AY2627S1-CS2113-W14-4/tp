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
    private String reason;

    /** Creates an order awaiting approval for the given item. */
    public Order(Item item) {
        this(item, OrderState.WAITING_APPROVAL);
    }

    /** Restores an order's recorded state and optional rejection reason. */
    public Order(Item item, OrderState state, String reason) {
        this.state = Objects.requireNonNull(state);
        this.item = Objects.requireNonNull(item);
        this.reason = reason;
    }

    /** Restores an order's recorded state without applying stock changes or replaying transitions. */
    public Order(Item item, OrderState state) {
        this(item, state, null);
    }

    public OrderState getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }

    /** Approves an order only while it is waiting for approval. */
    public void approve() throws StockHolmException {
        if (state != OrderState.WAITING_APPROVAL) {
            throw new StockHolmException("Order is not waiting for approval (current state: " + state + ").");
        }
        state = OrderState.WAITING_FOR_DELIVERY;
    }

    /**
     * Rejects an order with a reason only while it is waiting for approval.
     *
     * @param reason why the order was rejected
     * @throws StockHolmException if the order is not waiting for approval or the reason is blank
     */
    public void reject(String reason) throws StockHolmException {
        if (state != OrderState.WAITING_APPROVAL) {
            throw new StockHolmException("Order is not waiting for approval (current state: " + state + ").");
        }
        if (reason == null || reason.isBlank()) {
            throw new StockHolmException("Rejection reason cannot be blank.");
        }
        this.reason = reason;
        state = OrderState.REJECTED;
    }

    /**
     * Cancels an order before it reaches delivered.
     *
     * @throws StockHolmException if the order cannot be canceled
     */
    public void cancel() throws StockHolmException {
        if (state != OrderState.WAITING_APPROVAL && state != OrderState.WAITING_FOR_DELIVERY) {
            throw new StockHolmException("Order cannot be canceled (current state: " + state + ").");
        }
        state = OrderState.CANCELED;
    }

    /** Marks an order as delivered only while it is waiting for delivery. */
    public void deliver() throws StockHolmException {
        if (state != OrderState.WAITING_FOR_DELIVERY) {
            throw new StockHolmException("Order is not waiting for delivery (current state: " + state + ").");
        }
        state = OrderState.DELIVERED;
    }

    /**
     * Returns what kind of order this is, as shown to the user.
     *
     * @return {@code "Order"}; subclasses return their own label, e.g. {@code "Import order"}
     */
    public String getKindLabel() {
        return "Order";
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
