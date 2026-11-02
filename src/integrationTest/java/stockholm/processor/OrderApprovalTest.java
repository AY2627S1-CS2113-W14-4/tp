package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests approving and rejecting orders through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class OrderApprovalTest extends ProcessorTestBase {
    @Test
    public void process_approveImportAndExport_changesOnlyOrderStates() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);

        assertEquals(new Result("Approved order 2.", false),
                Processor.process(Parser.parseCommand("approve 2"), state));
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(1).getState());
        assertEquals(3.0, shop.getItems().get(0).getCount());

        Processor.process(Parser.parseCommand("approve 1"), state);
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
        assertEquals(3.0, shop.getItems().get(0).getCount());
        assertEquals(1, shop.getItems().size());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Import order (to Shop)\n   State: WAITING_FOR_DELIVERY"));
    }

    @Test
    public void process_approveAgain_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 1"), state));
        assertEquals("Order is not waiting for approval (current state: WAITING_FOR_DELIVERY).", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }

    @Test
    public void process_approveMissingOrder_throwsWithoutChangingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 2"), state));
        assertEquals("No order number 2. There are 1 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
    }

    @Test
    public void process_approveOutsideInventory_approvesOrder() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        state.leaveInventory();

        assertEquals(new Result("Approved order 1.", false),
                Processor.process(Parser.parseCommand("approve 1"), state));
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }

    @Test
    public void process_approveInOtherInventory_approvesOrderOfAnyInventory() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        Processor.process(Parser.parseCommand("approve 1"), state);

        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
        assertEquals("Shop", orders().get(0).getInventoryName());
    }

    @Test
    public void process_rejectImportOrder_setsRejectedStateAndReasonWithoutChangingStock()
            throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);

        Result result = Processor.process(Parser.parseCommand("reject 1 Damaged item"), state);
        assertEquals(new Result("Rejected order 1.", false), result);
        assertEquals(OrderState.REJECTED, orders().get(0).getState());
        assertEquals("Damaged item", orders().get(0).getReason());
        assertTrue(shop.getItems().isEmpty());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Import order (to Shop)\n   State: REJECTED\n   Reason: Damaged item"));
    }

    @Test
    public void process_rejectExportOrder_refundsStockAndSetsRejectedState() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        assertEquals(3.0, shop.getItems().get(0).getCount());

        Result result = Processor.process(Parser.parseCommand("reject 1 Out of stock"), state);
        assertEquals(new Result("Rejected order 1.", false), result);
        assertEquals(OrderState.REJECTED, orders().get(0).getState());
        assertEquals("Out of stock", orders().get(0).getReason());
        assertEquals(1, shop.getItems().size());
        assertEquals(5.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_refundExportOfRemovedItem_restoresItsMinimum() throws StockHolmException {
        for (String refund : new String[]{"reject 1 Damaged", "cancel 1"}) {
            setUp();
            Inventory shop = enterNewShop();
            Item rice = new Item("Rice", "Food", 2);
            rice.setMinCount(5);
            shop.addItem(rice);
            Processor.process(Parser.parseCommand("export 1"), state);
            assertTrue(shop.getItems().isEmpty());
            if (refund.startsWith("cancel")) {
                Processor.process(Parser.parseCommand("approve 1"), state);
            }

            Processor.process(Parser.parseCommand(refund), state);

            Item restored = shop.findItem("Rice");
            assertEquals(2.0, restored.getCount(), refund);
            assertEquals(5.0, restored.getMinCount(), refund);
        }
    }

    @Test
    public void process_deliverIntoDestination_doesNotCarryRecordedMinimum() throws StockHolmException {
        // Minimums are per inventory: one recorded with an order, e.g. a future transfer's source minimum,
        // must not follow the stock into the destination.
        Inventory shop = enterNewShop();
        Item pen = new Item("Pen", "", 2);
        pen.setMinCount(5);
        state.getOrderHistory().add(new ImportOrder(pen, shop, OrderState.WAITING_FOR_DELIVERY));

        Processor.process(Parser.parseCommand("deliver 1"), state);

        assertEquals(2.0, shop.findItem("Pen").getCount());
        assertEquals(0.0, shop.findItem("Pen").getMinCount());
    }

    @Test
    public void process_refundExportIntoExistingItem_keepsThatItemsMinimum() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        rice.setMinCount(3);
        shop.addItem(rice);
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("item min 1 4"), state);

        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);

        assertSame(rice, shop.findItem("Rice"));
        assertEquals(5.0, rice.getCount());
        assertEquals(4.0, rice.getMinCount());
    }

    @Test
    public void process_rejectExportOrderDeletedItem_restoresItemAndSetsRejectedState() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 2));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        assertTrue(shop.getItems().isEmpty());

        Result result = Processor.process(Parser.parseCommand("reject 1 Cancelled by customer"), state);
        assertEquals(new Result("Rejected order 1.", false), result);
        assertEquals(OrderState.REJECTED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals("Rice", shop.getItems().get(0).getName());
        assertEquals(2.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_rejectExportOrderDifferentType_throwsAndKeepsStateAndStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        shop.removeItem(0);
        shop.addItem(new Item("Rice", "Grain", 3));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("reject 1 Not needed"), state));
        assertEquals("Item already exists with a different type: Rice (Grain) x3.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
    }

    @Test
    public void process_rejectAlreadyRejected_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("reject 1 Damaged again"), state));
        assertEquals("Order is not waiting for approval (current state: REJECTED).", e.getMessage());
        assertEquals(OrderState.REJECTED, orders().get(0).getState());
    }

    @Test
    public void process_rejectAlreadyApproved_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("reject 1 Too late"), state));
        assertEquals("Order is not waiting for approval (current state: WAITING_FOR_DELIVERY).", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }

    @Test
    public void process_rejectMissingOrder_throwsWithoutChangingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("reject 2 Not found"), state));
        assertEquals("No order number 2. There are 1 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
    }

    @Test
    public void process_rejectOutsideInventory_rejectsOrder() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        state.leaveInventory();

        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);

        assertEquals(OrderState.REJECTED, orders().get(0).getState());
        assertEquals("Damaged", orders().get(0).getReason());
    }

    @Test
    public void process_rejectExportInOtherInventory_refundsSourceInventory() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);

        assertEquals(OrderState.REJECTED, orders().get(0).getState());
        assertEquals(5.0, shop.findItem("Rice").getCount());
        assertTrue(warehouse.getItems().isEmpty());
    }

    @Test
    public void process_rejectExportOfMissingInventory_throwsAndKeepsState() throws StockHolmException {
        // An order whose inventory is missing, as after loading orders with a corrupted inventory file.
        state.getOrderHistory().add(new ExportOrder(new Item("Rice", "Food", 2), new Inventory("Ghost")));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("reject 1 Damaged"), state));
        assertEquals("Inventory Ghost no longer exists.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
        assertNull(orders().get(0).getReason());
    }
}
