package stockholm.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/** Tests timestamps at creation, on successful transitions, and after rejected transitions. */
class OrderTimestampTest {
    private static final Instant SAVED_CREATION = Instant.parse("2026-10-01T10:00:00Z");

    /** Creates an order with equal creation and initial status timestamps. */
    @Test
    public void newOrder_recordsCreationAndInitialStatusTime() {
        Instant before = Instant.now();
        Inventory shop = new Inventory("Shop");
        Inventory warehouse = new Inventory("Warehouse");
        List<Order> orders = List.of(new ImportOrder(new Item("Pen", "", 1), shop),
                new ExportOrder(new Item("Pen", "", 1), shop),
                new TransferOrder(new Item("Pen", "", 1), shop, warehouse));
        Instant after = Instant.now();

        for (Order order : orders) {
            assertNotNull(order.getCreatedAt());
            assertEquals(order.getCreatedAt(), order.getLastStatusChangedAt());
            assertTrue(!order.getCreatedAt().isBefore(before) && !order.getCreatedAt().isAfter(after));
        }
    }

    /** Keeps creation time and advances the status timestamp on each successful transition. */
    @Test
    public void approveAndDeliver_updateOnlyStatusTimestamp() throws StockHolmException {
        Order order = savedOrder(OrderState.WAITING_APPROVAL);

        order.approve();
        Instant approvedAt = order.getLastStatusChangedAt();
        assertEquals(SAVED_CREATION, order.getCreatedAt());
        assertTrue(approvedAt.isAfter(SAVED_CREATION));

        order.deliver();
        assertEquals(SAVED_CREATION, order.getCreatedAt());
        assertTrue(!order.getLastStatusChangedAt().isBefore(approvedAt));
    }

    /** Records rejection, cancellation, and deletion cancellation as status changes. */
    @Test
    public void otherTransitions_updateStatusTimestamp() throws StockHolmException {
        Order rejected = savedOrder(OrderState.WAITING_APPROVAL);
        rejected.reject("Damaged");
        assertTrue(rejected.getLastStatusChangedAt().isAfter(SAVED_CREATION));

        Order canceled = savedOrder(OrderState.WAITING_FOR_DELIVERY);
        canceled.cancel();
        assertTrue(canceled.getLastStatusChangedAt().isAfter(SAVED_CREATION));

        OrderHistory history = new OrderHistory();
        Order deleted = savedOrder(OrderState.WAITING_APPROVAL);
        history.add(deleted);
        assertEquals(1, history.cancelOpenOrdersOf("Shop"));
        assertEquals(OrderState.CANCELED, deleted.getState());
        assertTrue(deleted.getLastStatusChangedAt().isAfter(SAVED_CREATION));
    }

    /** Does not change either timestamp when a transition is rejected. */
    @Test
    public void invalidTransition_keepsTimestamps() {
        Order order = savedOrder(OrderState.WAITING_APPROVAL);

        assertThrows(StockHolmException.class, order::deliver);

        assertEquals(SAVED_CREATION, order.getCreatedAt());
        assertEquals(SAVED_CREATION, order.getLastStatusChangedAt());
    }

    /** Keeps a legacy creation time unknown while recording a later successful change. */
    @Test
    public void legacyOrder_statusChangeDoesNotInventCreationTime() throws StockHolmException {
        Order order = new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"),
                OrderState.WAITING_APPROVAL, null, null, null);

        assertNull(order.getCreatedAt());
        assertNull(order.getLastStatusChangedAt());
        order.approve();
        assertNull(order.getCreatedAt());
        assertNotNull(order.getLastStatusChangedAt());
    }

    /** Restores an order with a fixed, past creation time for transition checks. */
    private static Order savedOrder(OrderState state) {
        return new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"), state, null,
                SAVED_CREATION, SAVED_CREATION);
    }
}
