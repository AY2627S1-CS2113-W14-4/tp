package stockholm.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/**
 * Tests for {@link OrderHistory}.
 */
class OrderHistoryTest {
    @Test
    public void getOrders_newHistory_isEmpty() {
        assertTrue(new OrderHistory().getOrders().isEmpty());
    }

    @Test
    public void add_ordersOfDifferentInventories_keptInCreationOrder() {
        OrderHistory history = new OrderHistory();
        ImportOrder pen = new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"));
        ExportOrder paper = new ExportOrder(new Item("Paper", "", 2), new Inventory("Warehouse"));

        history.add(pen);
        history.add(paper);

        assertEquals(2, history.getOrders().size());
        assertSame(pen, history.getOrders().get(0));
        assertSame(paper, history.getOrders().get(1));
        assertEquals("Shop", pen.getInventoryName());
        assertEquals("Warehouse", paper.getInventoryName());
    }

    @Test
    public void cancelOpenOrdersOf_mixedOrders_cancelsOnlyThatInventorysOpenOrders() throws StockHolmException {
        Inventory shop = new Inventory("Shop");
        OrderHistory history = new OrderHistory();
        ImportOrder pending = new ImportOrder(new Item("Pen", "", 1), shop);
        ExportOrder approved = new ExportOrder(new Item("Rice", "", 2), shop, OrderState.WAITING_FOR_DELIVERY);
        ImportOrder delivered = new ImportOrder(new Item("Ink", "", 1), shop, OrderState.DELIVERED);
        ImportOrder rejected = new ImportOrder(new Item("Box", "", 1), shop, OrderState.REJECTED, "Damaged");
        ImportOrder other = new ImportOrder(new Item("Cup", "", 1), new Inventory("Warehouse"));
        for (Order order : new Order[]{pending, approved, delivered, rejected, other}) {
            history.add(order);
        }

        assertEquals(2, history.cancelOpenOrdersOf("Shop"));

        assertEquals(OrderState.CANCELED, pending.getState());
        assertEquals(OrderState.CANCELED, approved.getState());
        assertEquals(OrderState.DELIVERED, delivered.getState());
        assertEquals(OrderState.REJECTED, rejected.getState());
        assertEquals("Damaged", rejected.getReason());
        assertEquals(OrderState.WAITING_APPROVAL, other.getState());
        assertEquals(0, history.cancelOpenOrdersOf("Shop"));
    }

    @Test
    public void getOrders_modified_throwsException() {
        OrderHistory history = new OrderHistory();
        assertThrows(UnsupportedOperationException.class,
                () -> history.getOrders().add(new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"))));
    }
}
