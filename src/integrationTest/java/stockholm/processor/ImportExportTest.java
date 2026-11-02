package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests creating import and export orders through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class ImportExportTest extends ProcessorTestBase {
    @Test
    public void process_importWithAllOptions_createsPendingOrderWithoutAddingStock() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(Parser.parseCommand("import \"A4 Paper Case\" --type=Stationery --count=2"),
                state);

        assertEquals(new Result("Import order created:\nItem name: A4 Paper Case"
                + "\nItem type: Stationery\nItem count: 2", false), result);
        assertEquals(1, orders().size());
        Order order = orders().get(0);
        assertTrue(order instanceof ImportOrder);
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
        assertEquals("A4 Paper Case", order.getItem().getName());
        assertEquals("Stationery", order.getItem().getType());
        assertEquals(2.0, order.getItem().getCount());
        assertTrue(shop.getItems().isEmpty());
    }

    @Test
    public void process_importWithoutOptions_usesDefaults() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(Parser.parseCommand("import Pen"), state);

        assertEquals("Import order created:\nItem name: Pen\nItem type: \nItem count: 1", result.message());
        Item item = orders().get(0).getItem();
        assertEquals("", item.getType());
        assertEquals(1.0, item.getCount());
    }

    @Test
    public void process_importDecimalCount_preservesDecimal() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(Parser.parseCommand("import Rice --count=2.5 --type=Food"), state);

        assertEquals("Import order created:\nItem name: Rice\nItem type: Food\nItem count: 2.5", result.message());
        assertEquals(2.5, orders().get(0).getItem().getCount());
    }

    @Test
    public void process_importExistingItem_createsSeparateOrdersAndKeepsStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        shop.addItem(rice);

        Processor.process(Parser.parseCommand("import Rice --type=Food --count=2"), state);
        Processor.process(Parser.parseCommand("import Rice --type=Food --count=3"), state);

        assertEquals(1, shop.getItems().size());
        assertSame(rice, shop.getItems().get(0));
        assertEquals(5.0, rice.getCount());
        assertEquals(2, orders().size());
        assertEquals(2.0, orders().get(0).getItem().getCount());
        assertEquals(3.0, orders().get(1).getItem().getCount());
        rice.addCount(1);
        assertEquals(2.0, orders().get(0).getItem().getCount());
    }

    @Test
    public void process_importAfterSwitchingInventory_storesOrderInCurrentInventory() throws StockHolmException {
        Inventory shop = enterNewShop();
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);

        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(new Command(CommandType.BACK), state);
        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);

        assertEquals(1, orders().size());
        assertEquals("Warehouse", orders().get(0).getInventoryName());
        assertEquals("Pen", orders().get(0).getItem().getName());
    }

    @Test
    public void process_importOutsideInventory_throwsAndCreatesNoOrder() throws StockHolmException {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        Command command = Parser.parseCommand("import Pen");

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(command, state));

        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        assertTrue(orders().isEmpty());
        assertTrue(shop.getItems().isEmpty());
    }

    @Test
    public void process_exportPartialCount_createsPendingOrderAndReducesStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        Item pen = new Item("Pen", "Stationery", 3);
        shop.addItem(pen);
        shop.addItem(rice);

        Result result = Processor.process(Parser.parseCommand("export 2 --count=2.5"), state);

        assertEquals(new Result("Export order created:\nItem name: Rice\nItem type: Food\nItem count: 2.5",
                false), result);
        assertEquals(2, shop.getItems().size());
        assertSame(rice, shop.getItems().get(1));
        assertEquals(2.5, rice.getCount());
        assertEquals(3.0, pen.getCount());
        assertEquals(1, orders().size());
        Order order = orders().get(0);
        assertTrue(order instanceof ExportOrder);
        assertEquals(OrderState.WAITING_APPROVAL, order.getState());
        assertEquals("Rice", order.getItem().getName());
        assertEquals("Food", order.getItem().getType());
        assertEquals(2.5, order.getItem().getCount());
        rice.addCount(1);
        assertEquals(2.5, order.getItem().getCount());
    }

    @Test
    public void process_exportAllStock_removesItemAndRetainsOrder() throws StockHolmException {
        for (String input : new String[]{"export 1", "export 1 --count=2.5"}) {
            setUp();
            Inventory shop = enterNewShop();
            Item rice = new Item("Rice", "Food", 2.5);
            Item pen = new Item("Pen", "", 1);
            shop.addItem(rice);
            shop.addItem(pen);

            Processor.process(Parser.parseCommand(input), state);

            assertEquals(0.0, rice.getCount());
            assertEquals(1, shop.getItems().size());
            assertSame(pen, shop.getItems().get(0));
            assertEquals(1, orders().size());
            assertEquals(2.5, orders().get(0).getItem().getCount());
            assertEquals("Items in Shop:\n1. Pen x1",
                    Processor.process(Parser.parseCommand("item list"), state).message());
        }
    }

    @Test
    public void process_exportExcessCount_throwsWithoutChangingStockOrOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 2.5);
        shop.addItem(rice);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 1 --count=3"), state));

        assertEquals("Cannot export 3 of Rice; only 2.5 available.", e.getMessage());
        assertEquals(2.5, rice.getCount());
        assertSame(rice, shop.getItems().get(0));
        assertTrue(orders().isEmpty());
    }

    @Test
    public void process_exportMissingItem_throwsWithoutCreatingOrder() throws StockHolmException {
        Inventory shop = enterNewShop();
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 1"), state));
        Item rice = new Item("Rice", "Food", 2.5);
        shop.addItem(rice);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 2"), state));

        assertEquals("No item number 2. Shop has 1 item(s); use item list to see them.", e.getMessage());
        assertEquals(2.5, rice.getCount());
        assertTrue(orders().isEmpty());
    }

    @Test
    public void process_exportRepeatedDecimals_usesRemainingStockAndPreservesOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 0.3));

        Processor.process(Parser.parseCommand("export 1 --count=0.1"), state);
        assertEquals(0.2, shop.getItems().get(0).getCount());
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 1 --count=0.3"), state));
        assertEquals(1, orders().size());
        Processor.process(Parser.parseCommand("export 1 --count=0.2"), state);

        assertTrue(shop.getItems().isEmpty());
        assertEquals(2, orders().size());
        assertEquals(0.1, orders().get(0).getItem().getCount());
        assertEquals(0.2, orders().get(1).getItem().getCount());
    }

    @Test
    public void process_exportOutsideInventory_throwsWithoutChangingData() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        shop.addItem(rice);
        state.leaveInventory();

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 1"), state));

        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        assertEquals(5.0, rice.getCount());
        assertTrue(orders().isEmpty());
    }

    @Test
    public void process_exportAfterSwitchingInventory_changesOnlyCurrentInventory() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        shop.addItem(rice);
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "", 3));
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        Processor.process(Parser.parseCommand("export 1 --count=2"), state);

        assertEquals(5.0, rice.getCount());
        assertEquals(1, orders().size());
        assertEquals(1.0, warehouse.getItems().get(0).getCount());
        assertEquals("Warehouse", orders().get(0).getInventoryName());
        assertEquals("Pen", orders().get(0).getItem().getName());
    }

    @Test
    public void process_exportInvalidStock_throwsWithoutCreatingOrder() throws StockHolmException {
        for (double count : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            setUp();
            Inventory shop = enterNewShop();
            shop.addItem(new Item("Rice", "Food", count));

            assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand("export 1"), state));

            assertEquals(count, shop.getItems().get(0).getCount());
            assertTrue(orders().isEmpty());
        }
    }

    @Test
    public void process_orderListWithExport_showsKindStateAndExportedCount() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("export 1 --count=2.5"), state);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders (Page 1 of 1):"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: \n   Item count: 1"
                + "\n2. Export order (from Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5", false), result);
        assertEquals(2.5, shop.getItems().get(0).getCount());
        assertEquals(2, orders().size());
    }
}
