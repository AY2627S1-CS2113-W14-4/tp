package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests canceling orders through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class OrderCancelTest extends ProcessorTestBase {
    @Test
    public void process_cancelWaitingApproval_throwsAndKeepsStateAndStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);

        for (String input : new String[]{"cancel 1", "cancel 2"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand(input), state));
            assertEquals("Order cannot be canceled (current state: WAITING_APPROVAL).", e.getMessage());
        }
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(1).getState());
        assertEquals(3.0, shop.findItem("Rice").getCount());
    }

    @Test
    public void process_cancelApprovedImportOrder_setsCanceledStateWithoutChangingStock()
            throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());

        Result result = Processor.process(Parser.parseCommand("cancel 1"), state);
        assertEquals(new Result("Canceled order 1.", false), result);
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertTrue(shop.getItems().isEmpty());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Import order (to Shop)\n   State: CANCELED"));
    }

    @Test
    public void process_cancelExportOrder_refundsStockAndSetsCanceledState() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        assertEquals(3.0, shop.getItems().get(0).getCount());
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("cancel 1"), state);
        assertEquals(new Result("Canceled order 1.", false), result);
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals(5.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_cancelExportOrderDeletedItem_restoresItemAndSetsCanceledState() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 2));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        assertTrue(shop.getItems().isEmpty());
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("cancel 1"), state);
        assertEquals(new Result("Canceled order 1.", false), result);
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals("Rice", shop.getItems().get(0).getName());
        assertEquals(2.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_cancelExportOrderDifferentType_throwsAndKeepsStateAndStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        shop.removeItem(0);
        shop.addItem(new Item("Rice", "Grain", 3));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));
        assertEquals("Item already exists with a different type: Rice (Grain) x3.", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }

    @Test
    public void process_cancelDeliveredExportOrderDifferentType_reportsStateBeforeTypeConflict()
            throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Processor.process(Parser.parseCommand("deliver 1"), state);
        shop.removeItem(0);
        shop.addItem(new Item("Rice", "Grain", 3));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));
        assertEquals("Order cannot be canceled (current state: DELIVERED).", e.getMessage());
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
    }

    @Test
    public void process_cancelAlreadyDelivered_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Processor.process(Parser.parseCommand("deliver 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));
        assertEquals("Order cannot be canceled (current state: DELIVERED).", e.getMessage());
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
    }

    @Test
    public void process_cancelAlreadyCanceled_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Processor.process(Parser.parseCommand("cancel 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));
        assertEquals("Order cannot be canceled (current state: CANCELED).", e.getMessage());
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
    }

    @Test
    public void process_cancelAlreadyRejected_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));
        assertEquals("Order cannot be canceled (current state: REJECTED).", e.getMessage());
        assertEquals(OrderState.REJECTED, orders().get(0).getState());
    }

    @Test
    public void process_cancelMissingOrder_throwsWithoutChangingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 2"), state));
        assertEquals("No order number 2. There are 1 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
    }

    @Test
    public void process_cancelOutsideInventory_cancelsOrder() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        state.leaveInventory();

        Processor.process(Parser.parseCommand("cancel 1"), state);

        assertEquals(OrderState.CANCELED, orders().get(0).getState());
    }

    @Test
    public void process_cancelExportInOtherInventory_refundsSourceInventory() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        Processor.process(Parser.parseCommand("cancel 1"), state);

        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertEquals(5.0, shop.findItem("Rice").getCount());
        assertTrue(warehouse.getItems().isEmpty());
    }

    @Test
    public void process_cancelOrdersOfMissingInventory_cancelsImportButNotExport() throws StockHolmException {
        // Orders whose inventory is missing, as after loading orders with a corrupted inventory file.
        Inventory ghost = new Inventory("Ghost");
        state.getOrderHistory().add(new ImportOrder(new Item("Pen", "", 1), ghost, OrderState.WAITING_FOR_DELIVERY));
        state.getOrderHistory().add(new ExportOrder(new Item("Rice", "", 2), ghost, OrderState.WAITING_FOR_DELIVERY));

        Processor.process(Parser.parseCommand("cancel 1"), state);
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 2"), state));

        assertEquals("Inventory Ghost no longer exists.", e.getMessage());
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(1).getState());
    }

    @Test
    public void process_cancelOrderOfDeletedInventory_reportsItIsAlreadyCanceled() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        state.leaveInventory();
        Processor.process(invCommand(CommandType.INV_DELETE, "Shop"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("cancel 1"), state));

        assertEquals("Order cannot be canceled (current state: CANCELED).", e.getMessage());
    }
}
