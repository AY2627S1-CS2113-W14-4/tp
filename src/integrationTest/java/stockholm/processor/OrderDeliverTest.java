package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ImportOrder;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests delivering orders through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class OrderDeliverTest extends ProcessorTestBase {
    @Test
    public void process_deliverImportOrderNewItem_adjustsStockAndSetsDelivered() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(new Result("Delivered order 1.", false), result);
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals("Pen", shop.getItems().get(0).getName());
        assertEquals(2.0, shop.getItems().get(0).getCount());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Import order (to Shop)\n   State: DELIVERED"));
    }

    @Test
    public void process_deliverImportOrderExistingItem_mergesStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Rice --type=Food --count=2.5"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(new Result("Delivered order 1.", false), result);
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals(7.5, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_deliverImportOrderExistingItemWithoutType_mergesStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Rice --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(new Result("Delivered order 1.", false), result);
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals(7.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_deliverImportOrderExistingItemDifferentType_throwsAndKeepsStateAndStock()
            throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Rice --type=Grain --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals("Item already exists with a different type: Rice (Food) x5.", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
        assertEquals(5.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_deliverExportOrder_changesOrderStateWithoutChangingStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        assertEquals(3.0, shop.getItems().get(0).getCount());
        Processor.process(Parser.parseCommand("approve 1"), state);

        Result result = Processor.process(Parser.parseCommand("deliver 1"), state);
        assertEquals(new Result("Delivered order 1.", false), result);
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(3.0, shop.getItems().get(0).getCount());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Export order (from Shop)\n   State: DELIVERED"));
    }

    @Test
    public void process_deliverWaitingApproval_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals("Order is not waiting for delivery (current state: WAITING_APPROVAL).", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(0).getState());
        assertTrue(shop.getItems().isEmpty());
    }

    @Test
    public void process_deliverAlreadyDelivered_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Processor.process(Parser.parseCommand("deliver 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 1"), state));
        assertEquals("Order is not waiting for delivery (current state: DELIVERED).", e.getMessage());
        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_deliverMissingOrder_throwsWithoutChangingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 2"), state));
        assertEquals("No order number 2. There are 1 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }

    @Test
    public void process_deliverImportOutsideInventory_addsStockToDestination() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        state.leaveInventory();

        Processor.process(Parser.parseCommand("deliver 1"), state);

        assertEquals(OrderState.DELIVERED, orders().get(0).getState());
        assertEquals(2.0, shop.findItem("Pen").getCount());
    }

    @Test
    public void process_deliverImportInOtherInventory_addsStockToDestinationOnly() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        Processor.process(Parser.parseCommand("deliver 1"), state);

        assertEquals(2.0, shop.findItem("Pen").getCount());
        assertTrue(warehouse.getItems().isEmpty());
    }

    @Test
    public void process_deliverImportOfMissingInventory_throwsAndKeepsState() throws StockHolmException {
        // An order whose inventory is missing, as after loading orders with a corrupted inventory file.
        state.getOrderHistory().add(
                new ImportOrder(new Item("Pen", "", 1), new Inventory("Ghost"), OrderState.WAITING_FOR_DELIVERY));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("deliver 1"), state));

        assertEquals("Inventory Ghost no longer exists.", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(0).getState());
    }
}
