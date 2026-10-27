package stockholm.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
    public void getOrders_modified_throwsException() {
        OrderHistory history = new OrderHistory();
        assertThrows(UnsupportedOperationException.class,
                () -> history.getOrders().add(new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"))));
    }
}
