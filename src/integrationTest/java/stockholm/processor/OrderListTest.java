package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests the paginated, filterable {@code order list} command through {@link Processor#process(Command, AppState)},
 * using commands built by {@link stockholm.parser.Parser} or directly.
 */
class OrderListTest extends ProcessorTestBase {
    @Test
    public void process_orderListLargePageSize_doesNotOverflow() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Result result = Processor.process(
                Parser.parseCommand("order list 1 --size=" + Integer.MAX_VALUE), state);
        assertTrue(result.message().startsWith("Orders (Page 1 of 1):"));
        assertTrue(result.message().contains("1. Import order (to Shop)"));
    }

    @Test
    public void process_orderListWithFilters_filtersByStatusInventoryAndType() throws StockHolmException {
        enterNewShop(); // Shop
        // Order 1: Import (Shop), WAITING_APPROVAL
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);

        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Box", "Packaging", 10));
        inventories.add(warehouse);
        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);
        // Order 2: Export (Warehouse), WAITING_APPROVAL -> WAITING_FOR_DELIVERY
        Processor.process(Parser.parseCommand("export 1 --count=3"), state);
        Processor.process(Parser.parseCommand("approve 2"), state);

        // Order 3: Transfer, WAITING_APPROVAL
        Processor.process(Parser.parseCommand("transfer 1 --from=Warehouse --to=Shop --count=1"), state);

        // Filter by status WAITING_FOR_DELIVERY: only Order 2
        Result statusResult = Processor.process(Parser.parseCommand("order list --status=WAITING_FOR_DELIVERY"), state);
        assertTrue(statusResult.message().startsWith("Orders (Page 1 of 1):"));
        assertFalse(statusResult.message().contains("1. Import order"));
        assertTrue(statusResult.message().contains("2. Export order (from Warehouse)"));
        assertFalse(statusResult.message().contains("3. Transfer order"));

        // Filter by inventory Shop: Order 1 (dst Shop) and Order 3 (dst Shop)
        Result invResult = Processor.process(Parser.parseCommand("order list --inv=Shop"), state);
        assertTrue(invResult.message().contains("1. Import order (to Shop)"));
        assertFalse(invResult.message().contains("2. Export order (from Warehouse)"));
        assertTrue(invResult.message().contains("3. Transfer order (from Warehouse to Shop)"));

        // Case-sensitive inventory matching: "shop" does not match "Shop"
        Result caseResult = Processor.process(Parser.parseCommand("order list --inv=shop"), state);
        assertEquals(new Result("No matching orders found.", false), caseResult);

        // Filter by order type transfer: only Order 3
        Result typeResult = Processor.process(Parser.parseCommand("order list --type=transfer"), state);
        assertFalse(typeResult.message().contains("1. Import order"));
        assertFalse(typeResult.message().contains("2. Export order"));
        assertTrue(typeResult.message().contains("3. Transfer order (from Warehouse to Shop)"));

        // Combined filter with no matches
        Result noMatchResult = Processor.process(
                Parser.parseCommand("order list --status=DELIVERED --type=import"), state);
        assertEquals(new Result("No matching orders found.", false), noMatchResult);

        // Out of range page for matching filter
        StockHolmException pageError = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("order list 2 --type=transfer"), state));
        assertEquals("No page number 2. There are 1 page(s); use order list to see the first page.",
                pageError.getMessage());
    }

    @Test
    public void process_orderListTypeImportOrExport_excludesOtherKinds() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("item add Rice --count=5"), state);
        Processor.process(Parser.parseCommand("export 1 --count=1"), state);

        Result importResult = Processor.process(Parser.parseCommand("order list --type=import"), state);
        assertTrue(importResult.message().contains("1. Import order (to Shop)"));
        assertFalse(importResult.message().contains("Export order"));

        Result exportResult = Processor.process(Parser.parseCommand("order list --type=export"), state);
        assertFalse(exportResult.message().contains("Import order"));
        assertTrue(exportResult.message().contains("2. Export order (from Shop)"));
    }

    @Test
    public void process_orderListLaterPageWithNoMatches_throwsException() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("order list 2 --type=transfer"), state));
        assertEquals("No page number 2. There are no matching orders.", e.getMessage());
    }

    @Test
    public void process_orderListEmptyPageOne_returnsNoOrdersMessage() throws StockHolmException {
        enterNewShop();
        Result result = Processor.process(Parser.parseCommand("order list 1"), state);
        assertEquals(new Result("No orders yet.", false), result);
    }

    @Test
    public void process_orderListPageOutOfRangeEmpty_throwsException() throws StockHolmException {
        enterNewShop();
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("order list 2"), state));
        assertEquals("No page number 2. There are no orders yet.", e.getMessage());
    }

    @Test
    public void process_orderListPaginated_showsRequestedPageAndRetainsOrderIndices() throws StockHolmException {
        enterNewShop();
        for (int i = 1; i <= 12; i++) {
            Processor.process(Parser.parseCommand("import Item" + i + " --count=" + i), state);
        }

        Result page1 = Processor.process(Parser.parseCommand("order list 1 --size=5"), state);
        assertTrue(page1.message().startsWith("Orders (Page 1 of 3):"));
        assertTrue(page1.message().contains("1. Import order"));
        assertTrue(page1.message().contains("5. Import order"));
        assertFalse(page1.message().contains("6. Import order"));

        Result page2 = Processor.process(Parser.parseCommand("order list 2 --size=5"), state);
        assertTrue(page2.message().startsWith("Orders (Page 2 of 3):"));
        assertFalse(page2.message().contains("5. Import order"));
        assertTrue(page2.message().contains("6. Import order"));
        assertTrue(page2.message().contains("10. Import order"));
        assertFalse(page2.message().contains("11. Import order"));

        Result page3 = Processor.process(Parser.parseCommand("order list 3 --size=5"), state);
        assertTrue(page3.message().startsWith("Orders (Page 3 of 3):"));
        assertFalse(page3.message().contains("10. Import order"));
        assertTrue(page3.message().contains("11. Import order"));
        assertTrue(page3.message().contains("12. Import order"));

        // Approve order 11 shown on page 3
        Processor.process(Parser.parseCommand("approve 11"), state);
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders().get(10).getState());

        StockHolmException outOfRange = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("order list 4 --size=5"), state));
        assertEquals("No page number 4. There are 3 page(s); use order list to see the first page.",
                outOfRange.getMessage());
    }

    @Test
    public void process_orderListEmpty_returnsNoOrdersMessage() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Pen", "", 1));

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("No orders yet.", false), result);
    }

    @Test
    public void process_orderListNonEmpty_returnsAllDetailsWithoutChangingData() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item stock = new Item("Rice", "Food", 5);
        shop.addItem(stock);
        Processor.process(Parser.parseCommand("import Rice --type=Food --count=2.5"), state);
        Processor.process(Parser.parseCommand("import Rice --count=1"), state);
        Order first = orders().get(0);
        Order second = orders().get(1);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders (Page 1 of 1):"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5"
                + "\n2. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: \n   Item count: 1", false),
                withoutOrderTimestamps(result));
        assertEquals(2, orders().size());
        assertSame(first, orders().get(0));
        assertSame(second, orders().get(1));
        assertEquals(OrderState.WAITING_APPROVAL, first.getState());
        assertEquals(OrderState.WAITING_APPROVAL, second.getState());
        assertEquals(2.5, first.getItem().getCount());
        assertEquals(1.0, second.getItem().getCount());
        assertEquals(1, shop.getItems().size());
        assertSame(stock, shop.getItems().get(0));
        assertEquals(5.0, stock.getCount());
        assertSame(shop, state.getCurrentInventory());
    }

    @Test
    public void process_orderListAfterSwitchingInventory_listsOrdersOfEveryInventory() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Rice"), state);
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "Stationery", 5));
        inventories.add(warehouse);
        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);
        Processor.process(Parser.parseCommand("export 1 --count=3"), state);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders (Page 1 of 1):"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: \n   Item count: 1"
                + "\n2. Export order (from Warehouse)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: Stationery\n   Item count: 3", false),
                withoutOrderTimestamps(result));
    }

    @Test
    public void process_orderListOutsideInventory_listsEveryOrder() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(new Command(CommandType.BACK), state);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders (Page 1 of 1):\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: \n   Item count: 1", false),
                withoutOrderTimestamps(result));
        assertFalse(state.isInsideInventory());
    }
}
