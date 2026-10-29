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
    public void getKindLabel_eachOrderKind_returnsDisplayLabel() {
        Item item = new Item("Pen", "", 1);
        Inventory shop = new Inventory("Shop");
        assertEquals("Order", new Order(item).getKindLabel());
        assertEquals("Import order", new ImportOrder(item, shop).getKindLabel());
        assertEquals("Export order", new ExportOrder(item, shop).getKindLabel());
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
    public void reject_fromWaitingApproval_changesToRejectedAndSetsReason() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.reject("Damaged goods");
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
    }

    @Test
    public void reject_notWaitingApproval_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.reject("Damaged goods"));
        assertEquals("Order is not waiting for approval (current state: WAITING_FOR_DELIVERY).", e.getMessage());
    }

    @Test
    public void reject_blankOrNullReason_throwsException() {
        Order order = new Order(new Item("Pen", "", 1));
        StockHolmException e1 = assertThrows(StockHolmException.class, () -> order.reject(""));
        assertEquals("Rejection reason cannot be blank.", e1.getMessage());

        StockHolmException e2 = assertThrows(StockHolmException.class, () -> order.reject("   "));
        assertEquals("Rejection reason cannot be blank.", e2.getMessage());

        StockHolmException e3 = assertThrows(StockHolmException.class, () -> order.reject(null));
        assertEquals("Rejection reason cannot be blank.", e3.getMessage());
    }

    @Test
    public void isOpen_onlyWhileWaitingForApprovalOrDelivery() {
        for (OrderState state : OrderState.values()) {
            boolean expected = state == OrderState.WAITING_APPROVAL || state == OrderState.WAITING_FOR_DELIVERY;
            assertEquals(expected, new Order(new Item("Pen", "", 1), state).isOpen(), state.toString());
        }
    }

    @Test
    public void cancel_fromWaitingApproval_throwsAndKeepsState() {
        Order order = new Order(new Item("Pen", "", 1));
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.cancel());
        assertEquals("Order cannot be canceled (current state: WAITING_APPROVAL).", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
    }

    @Test
    public void cancel_fromWaitingForDelivery_changesToCanceled() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        order.cancel();
        assertEquals(OrderState.CANCELED, order.getState());
    }

    @Test
    public void cancel_alreadyDelivered_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        order.deliver();
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.cancel());
        assertEquals("Order cannot be canceled (current state: DELIVERED).", e.getMessage());
    }

    @Test
    public void cancel_alreadyCanceled_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.approve();
        order.cancel();
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.cancel());
        assertEquals("Order cannot be canceled (current state: CANCELED).", e.getMessage());
    }

    @Test
    public void cancel_alreadyRejected_throwsException() throws StockHolmException {
        Order order = new Order(new Item("Pen", "", 1));
        order.reject("Damaged goods");
        StockHolmException e = assertThrows(StockHolmException.class, () -> order.cancel());
        assertEquals("Order cannot be canceled (current state: REJECTED).", e.getMessage());
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
    public void constructor_withStateAndReason_restoresStateAndReason() {
        Item item = new Item("Pen", "Stationery", 2);
        Order order = new Order(item, OrderState.REJECTED, "Damaged goods");
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
        assertSame(item, order.getItem());
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
    public void importOrder_constructorWithReason_setsReasonAndDestination() {
        Inventory shop = new Inventory("Shop");
        Item item = new Item("Pen", "", 1);
        ImportOrder order = new ImportOrder(item, shop, OrderState.REJECTED, "Damaged goods");
        assertEquals("Shop", order.getDst());
        assertNull(order.getSrc());
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
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

    @Test
    public void exportOrder_constructorWithReason_setsReasonAndSource() {
        Inventory shop = new Inventory("Shop");
        Item item = new Item("Pen", "", 1);
        ExportOrder order = new ExportOrder(item, shop, OrderState.REJECTED, "Damaged goods");
        assertEquals("Shop", order.getSrc());
        assertNull(order.getDst());
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
    }
}
