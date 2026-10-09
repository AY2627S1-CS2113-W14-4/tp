package stockholm.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/**
 * Tests for {@link Order}, {@link ImportOrder}, and {@link ExportOrder}.
 */
class OrderTest {
    @Test
    public void constructor_initialState_isWaitingApproval() {
        Item item = new Item("Pen", "Stationery", 2);
        Order order = new Order(item);
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
        assertSame(item, order.getItem());
        assertNull(order.getSrc());
        assertNull(order.getDst());
    }

    @Test
    public void approve_fromWaitingApproval_changesToWaitingForDelivery() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        assertEquals(OrderState.WAITING_FOR_DELIVERY, order.getState());
    }

    @Test
    public void approve_notWaitingApproval_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.approve());
        assertEquals("Order is not waiting for approval (current state: WAITING_FOR_DELIVERY).", e.getMessage());
    }

    @Test
    public void deliver_fromWaitingForDelivery_changesToDelivered() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        order.deliver();
        assertEquals(OrderState.DELIVERED, order.getState());
    }

    @Test
    public void deliver_notWaitingForDelivery_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        StockHolmException e1 = assertThrows(StockHolmException.class, () -> order.deliver());
        assertEquals("Order is not waiting for delivery (current state: WAITING_APPROVAL).", e1.getMessage());

        order.approve();
        order.deliver();
        StockHolmException e2 = assertThrows(StockHolmException.class, () -> order.deliver());
        assertEquals("Order is not waiting for delivery (current state: DELIVERED).", e2.getMessage());
    }

    @Test
    public void srcAndDst_settersAndGetters_workCorrectly() {
        Order order = new Order(new Item("Pen", "", 1));
        order.setSrc("Shop");
        order.setDst("Warehouse");
        assertEquals("Shop", order.getSrc());
        assertEquals("Warehouse", order.getDst());
    }

    @Test
    public void importOrder_constructor_setsDestination() {
        Inventory shop = new Inventory("Shop");
        Item item = new Item("Pen", "", 1);
        ImportOrder order = new ImportOrder(item, shop);
        assertEquals("Shop", order.getDst());
        assertNull(order.getSrc());
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
    }

    @Test
    public void exportOrder_constructor_setsSource() {
        Inventory shop = new Inventory("Shop");
        Item item = new Item("Pen", "", 1);
        ExportOrder order = new ExportOrder(item, shop);
        assertEquals("Shop", order.getSrc());
        assertNull(order.getDst());
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
    }
}
