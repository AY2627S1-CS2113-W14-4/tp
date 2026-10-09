package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
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
 * Tests for {@link Processor#process(Command, AppState)}.
 * Each test gets a fresh, empty {@link AppState} so tests cannot affect one another.
 */
class ProcessorTest {
    private AppState state;
    /** Shortcut to {@code state.getInventories()}. */
    private ArrayList<Inventory> inventories;

    @BeforeEach
    public void setUp() {
        state = new AppState();
        inventories = state.getInventories();
    }

    /** Builds an {@code inv} or {@code enter} command that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }

    /**
     * Builds an {@code item add} command.
     *
     * @param type  the {@code --type} value, or {@code null} to leave it out
     * @param count the {@code --count} value, or {@code null} to leave it out
     */
    private static Command itemAdd(String name, String type, String count) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_NAME, name);
        if (type != null) {
            args.put(ArgKey.ITEM_TYPE, type);
        }
        if (count != null) {
            args.put(ArgKey.ITEM_COUNT, count);
        }
        return new Command(CommandType.ITEM_ADD, args);
    }

    private static Command itemDelete(int index) {
        return new Command(CommandType.ITEM_DELETE, Map.of(ArgKey.ITEM_INDEX, String.valueOf(index)));
    }

    /** Creates an inventory named "Shop" and enters it. */
    private Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }

    // ---------- quit / no-op ----------

    @Test
    public void process_noOp_returnsEmptyNonExitResult() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.NO_OP), state);
        assertEquals(new Result("", false), result);
    }

    @Test
    public void process_quit_returnsExitResult() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.QUIT), state);
        assertTrue(result.isExit());
        assertEquals("", result.message());
    }

    // ---------- inv ----------

    @Test
    public void process_invAdd_addsInventory() throws StockHolmException {
        Result result = Processor.process(invCommand(CommandType.INV_ADD, "Shop"), state);

        assertEquals("Added inventory: Shop", result.message());
        assertFalse(result.isExit());
        assertEquals(1, inventories.size());
        assertEquals("Shop", inventories.get(0).getName());
    }

    @Test
    public void process_invAddDuplicate_throwsException() throws StockHolmException {
        Processor.process(invCommand(CommandType.INV_ADD, "Shop"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(invCommand(CommandType.INV_ADD, "Shop"), state));
        assertEquals("Inventory already exists: Shop", e.getMessage());
        assertEquals(1, inventories.size());
    }

    @Test
    public void process_invDeleteExisting_removesInventory() throws StockHolmException {
        Processor.process(invCommand(CommandType.INV_ADD, "Shop"), state);
        Processor.process(invCommand(CommandType.INV_ADD, "Warehouse"), state);

        Result result = Processor.process(invCommand(CommandType.INV_DELETE, "Shop"), state);

        assertEquals("Deleted inventory: Shop", result.message());
        assertEquals(1, inventories.size());
        assertEquals("Warehouse", inventories.get(0).getName());
    }

    @Test
    public void process_invDeleteMissing_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(invCommand(CommandType.INV_DELETE, "Ghost"), state));
        assertEquals("No such inventory: Ghost", e.getMessage());
    }

    @Test
    public void process_invListEmpty_returnsNoInventoriesMessage() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.INV_LIST), state);
        assertEquals(new Result("No inventories yet.", false), result);
    }

    @Test
    public void process_invListNonEmpty_returnsNumberedList() throws StockHolmException {
        inventories.add(new Inventory("Shop"));
        inventories.add(new Inventory("Warehouse"));

        Result result = Processor.process(new Command(CommandType.INV_LIST), state);

        assertEquals("Inventories:\n1. Shop\n2. Warehouse", result.message());
        assertFalse(result.isExit());
    }

    @Test
    public void process_invCommandsInsideInventory_throwException() {
        enterNewShop();
        Command[] invCommands = {
            invCommand(CommandType.INV_ADD, "Other"),
            invCommand(CommandType.INV_DELETE, "Shop"),
            new Command(CommandType.INV_LIST),
        };
        for (Command command : invCommands) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(command, state));
            assertEquals("You are inside Shop. Use back to leave it first.", e.getMessage());
        }
        assertEquals(1, inventories.size());
    }

    // ---------- enter / back ----------

    @Test
    public void process_enterExisting_setsCurrentInventory() throws StockHolmException {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);

        Result result = Processor.process(invCommand(CommandType.ENTER, "Shop"), state);

        assertEquals("Entered inventory: Shop", result.message());
        assertSame(shop, state.getCurrentInventory());
    }

    @Test
    public void process_enterMissing_throwsAndStaysOutside() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(invCommand(CommandType.ENTER, "Ghost"), state));
        assertEquals("No such inventory: Ghost", e.getMessage());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_enterWhileInside_switchesInventory() throws StockHolmException {
        enterNewShop();
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);

        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);

        assertSame(warehouse, state.getCurrentInventory());
    }

    @Test
    public void process_backInside_leavesInventory() throws StockHolmException {
        enterNewShop();

        Result result = Processor.process(new Command(CommandType.BACK), state);

        assertEquals("Left inventory: Shop", result.message());
        assertNull(state.getCurrentInventory());
    }

    @Test
    public void process_backOutside_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(new Command(CommandType.BACK), state));
        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
    }

    // ---------- item ----------

    @Test
    public void process_itemCommandsOutsideInventory_throwException() {
        Command[] itemCommands = {itemAdd("Pen", null, null), new Command(CommandType.ITEM_LIST), itemDelete(1)};
        for (Command command : itemCommands) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(command, state));
            assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        }
    }

    @Test
    public void process_itemAddWithAllOptions_addsItem() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);

        assertEquals("Added item: A4 Paper Case (Stationery) x2", result.message());
        Item item = shop.getItems().get(0);
        assertEquals("A4 Paper Case", item.getName());
        assertEquals("Stationery", item.getType());
        assertEquals(2.0, item.getCount());
    }

    @Test
    public void process_itemAddWithoutOptions_usesDefaults() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(itemAdd("Pen", null, null), state);

        assertEquals("Added item: Pen x1", result.message());
        assertEquals("", shop.getItems().get(0).getType());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddExistingName_mergesCounts() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "2.5"), state);

        Result result = Processor.process(itemAdd("Rice", null, "1.5"), state);

        assertEquals("Added 1.5 to existing item: Rice (Food) x4", result.message());
        assertEquals(1, shop.getItems().size());
        assertEquals(4.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddExistingNameSameType_mergesCounts() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "1"), state);

        Processor.process(itemAdd("Rice", "Food", "1"), state);

        assertEquals(2.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddExistingNameDifferentType_throwsAndKeepsCount() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(itemAdd("Rice", "Grain", "1"), state));

        assertEquals("Item already exists with a different type: Rice (Food) x1. "
                + "Leave out --type or use the same type.", e.getMessage());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    // ---------- approve ----------

    @Test
    public void process_approveImportAndExport_changesOnlyOrderStates() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen --count=2"), state);
        Processor.process(Parser.parseCommand("export 1 2"), state);

        assertEquals(new Result("Approved order 2 in Shop.", false),
                Processor.process(Parser.parseCommand("approve 2"), state));
        assertEquals(OrderState.WAITING_APPROVAL, shop.getOrders().get(0).getState());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, shop.getOrders().get(1).getState());
        assertEquals(3.0, shop.getItems().get(0).getCount());

        Processor.process(Parser.parseCommand("approve 1"), state);
        assertEquals(OrderState.WAITING_FOR_DELIVERY, shop.getOrders().get(0).getState());
        assertEquals(3.0, shop.getItems().get(0).getCount());
        assertEquals(1, shop.getItems().size());
        assertTrue(Processor.process(Parser.parseCommand("order list"), state).message()
                .contains("1. Import order\n   State: WAITING_FOR_DELIVERY"));
    }

    @Test
    public void process_approveAgain_throwsAndKeepsState() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("approve 1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 1"), state));
        assertEquals("Order is not waiting for approval (current state: WAITING_FOR_DELIVERY).", e.getMessage());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, shop.getOrders().get(0).getState());
    }

    @Test
    public void process_approveMissingOrder_throwsWithoutChangingOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 2"), state));
        assertEquals("No order number 2. Shop has 1 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, shop.getOrders().get(0).getState());
    }

    @Test
    public void process_approveOutsideInventory_throwsWithoutChangingOrder() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        state.leaveInventory();

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 1"), state));
        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, shop.getOrders().get(0).getState());
    }

    @Test
    public void process_approveUsesCurrentInventory_only() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 1"), state));
        assertEquals("No order number 1. Warehouse has 0 order(s); use order list to see them.", e.getMessage());
        assertEquals(OrderState.WAITING_APPROVAL, shop.getOrders().get(0).getState());
    }

    // ---------- import ----------

    @Test
    public void process_importWithAllOptions_createsPendingOrderWithoutAddingStock() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(Parser.parseCommand("import \"A4 Paper Case\" --type=Stationery --count=2"),
                state);

        assertEquals(new Result("Import order created:\nItem name: A4 Paper Case"
                + "\nItem type: Stationery\nItem count: 2", false), result);
        assertEquals(1, shop.getOrders().size());
        Order order = shop.getOrders().get(0);
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
        Item item = shop.getOrders().get(0).getItem();
        assertEquals("", item.getType());
        assertEquals(1.0, item.getCount());
    }

    @Test
    public void process_importDecimalCount_preservesDecimal() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(Parser.parseCommand("import Rice --count=2.5 --type=Food"), state);

        assertEquals("Import order created:\nItem name: Rice\nItem type: Food\nItem count: 2.5", result.message());
        assertEquals(2.5, shop.getOrders().get(0).getItem().getCount());
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
        assertEquals(2, shop.getOrders().size());
        assertEquals(2.0, shop.getOrders().get(0).getItem().getCount());
        assertEquals(3.0, shop.getOrders().get(1).getItem().getCount());
        rice.addCount(1);
        assertEquals(2.0, shop.getOrders().get(0).getItem().getCount());
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

        assertTrue(shop.getOrders().isEmpty());
        assertEquals(1, warehouse.getOrders().size());
        assertEquals("Pen", warehouse.getOrders().get(0).getItem().getName());
    }

    @Test
    public void process_importOutsideInventory_throwsAndCreatesNoOrder() throws StockHolmException {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        Command command = Parser.parseCommand("import Pen");

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(command, state));

        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        assertTrue(shop.getOrders().isEmpty());
        assertTrue(shop.getItems().isEmpty());
    }

    // ---------- export ----------

    @Test
    public void process_exportPartialCount_createsPendingOrderAndReducesStock() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 5);
        Item pen = new Item("Pen", "Stationery", 3);
        shop.addItem(pen);
        shop.addItem(rice);

        Result result = Processor.process(Parser.parseCommand("export 2 2.5"), state);

        assertEquals(new Result("Export order created:\nItem name: Rice\nItem type: Food\nItem count: 2.5",
                false), result);
        assertEquals(2, shop.getItems().size());
        assertSame(rice, shop.getItems().get(1));
        assertEquals(2.5, rice.getCount());
        assertEquals(3.0, pen.getCount());
        assertEquals(1, shop.getOrders().size());
        Order order = shop.getOrders().get(0);
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
        for (String input : new String[]{"export 1", "export 1 2.5"}) {
            Inventory shop = enterNewShop();
            Item rice = new Item("Rice", "Food", 2.5);
            Item pen = new Item("Pen", "", 1);
            shop.addItem(rice);
            shop.addItem(pen);

            Processor.process(Parser.parseCommand(input), state);

            assertEquals(0.0, rice.getCount());
            assertEquals(1, shop.getItems().size());
            assertSame(pen, shop.getItems().get(0));
            assertEquals(1, shop.getOrders().size());
            assertEquals(2.5, shop.getOrders().get(0).getItem().getCount());
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
                () -> Processor.process(Parser.parseCommand("export 1 3"), state));

        assertEquals("Cannot export 3 of Rice; only 2.5 available.", e.getMessage());
        assertEquals(2.5, rice.getCount());
        assertSame(rice, shop.getItems().get(0));
        assertTrue(shop.getOrders().isEmpty());
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
        assertTrue(shop.getOrders().isEmpty());
    }

    @Test
    public void process_exportRepeatedDecimals_usesRemainingStockAndPreservesOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 0.3));

        Processor.process(Parser.parseCommand("export 1 0.1"), state);
        assertEquals(0.2, shop.getItems().get(0).getCount());
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("export 1 0.3"), state));
        assertEquals(1, shop.getOrders().size());
        Processor.process(Parser.parseCommand("export 1 0.2"), state);

        assertTrue(shop.getItems().isEmpty());
        assertEquals(2, shop.getOrders().size());
        assertEquals(0.1, shop.getOrders().get(0).getItem().getCount());
        assertEquals(0.2, shop.getOrders().get(1).getItem().getCount());
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
        assertTrue(shop.getOrders().isEmpty());
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

        Processor.process(Parser.parseCommand("export 1 2"), state);

        assertEquals(5.0, rice.getCount());
        assertTrue(shop.getOrders().isEmpty());
        assertEquals(1.0, warehouse.getItems().get(0).getCount());
        assertEquals("Pen", warehouse.getOrders().get(0).getItem().getName());
    }

    @Test
    public void process_exportInvalidStock_throwsWithoutCreatingOrder() throws StockHolmException {
        for (double count : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Inventory shop = enterNewShop();
            shop.addItem(new Item("Rice", "Food", count));

            assertThrows(StockHolmException.class,
                    () -> Processor.process(Parser.parseCommand("export 1"), state));

            assertEquals(count, shop.getItems().get(0).getCount());
            assertTrue(shop.getOrders().isEmpty());
        }
    }

    @Test
    public void process_orderListWithExport_showsKindStateAndExportedCount() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("export 1 2.5"), state);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders in Shop:"
                + "\n1. Import order\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: \n   Item count: 1"
                + "\n2. Export order\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5", false), result);
        assertEquals(2.5, shop.getItems().get(0).getCount());
        assertEquals(2, shop.getOrders().size());
    }

    // ---------- order list ----------

    @Test
    public void process_orderListEmpty_returnsNoOrdersMessage() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Pen", "", 1));

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("No orders in Shop yet.", false), result);
    }

    @Test
    public void process_orderListNonEmpty_returnsAllDetailsWithoutChangingData() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item stock = new Item("Rice", "Food", 5);
        shop.addItem(stock);
        Processor.process(Parser.parseCommand("import Rice --type=Food --count=2.5"), state);
        Processor.process(Parser.parseCommand("import Rice --count=1"), state);
        Order first = shop.getOrders().get(0);
        Order second = shop.getOrders().get(1);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders in Shop:"
                + "\n1. Import order\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5"
                + "\n2. Import order\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: \n   Item count: 1", false), result);
        assertEquals(2, shop.getOrders().size());
        assertSame(first, shop.getOrders().get(0));
        assertSame(second, shop.getOrders().get(1));
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
    public void process_orderListAfterSwitchingInventory_listsOnlyCurrentOrders() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Rice"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        Processor.process(invCommand(CommandType.ENTER, "Warehouse"), state);

        assertEquals(new Result("No orders in Warehouse yet.", false),
                Processor.process(Parser.parseCommand("order list"), state));
        warehouse.addOrder(new Order(new Item("Pen", "Stationery", 3)));

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders in Warehouse:\n1. Order\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: Stationery\n   Item count: 3", false), result);
    }

    @Test
    public void process_orderListOutsideInventory_throwsException() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(new Command(CommandType.BACK), state);
        Command command = Parser.parseCommand("order list");

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(command, state));

        assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_itemListEmpty_returnsNoItemsMessage() throws StockHolmException {
        enterNewShop();
        Result result = Processor.process(new Command(CommandType.ITEM_LIST), state);
        assertEquals("No items in Shop yet.", result.message());
    }

    @Test
    public void process_itemListNonEmpty_returnsNumberedList() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);
        Processor.process(itemAdd("Pen", null, null), state);

        Result result = Processor.process(new Command(CommandType.ITEM_LIST), state);

        assertEquals("Items in Shop:\n1. A4 Paper Case (Stationery) x2\n2. Pen x1", result.message());
    }

    @Test
    public void process_itemDeleteValidIndex_removesThatItem() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("A", null, null), state);
        Processor.process(itemAdd("B", null, null), state);
        Processor.process(itemAdd("C", null, null), state);

        Result result = Processor.process(itemDelete(2), state);

        assertEquals("Deleted item: B x1", result.message());
        assertEquals(2, shop.getItems().size());
        assertEquals("A", shop.getItems().get(0).getName());
        assertEquals("C", shop.getItems().get(1).getName());
    }

    @Test
    public void process_itemDeleteIndexTooLarge_throwsException() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A", null, null), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(itemDelete(2), state));
        assertEquals("No item number 2. Shop has 1 item(s); use item list to see them.", e.getMessage());
    }

    // ---------- stock ----------

    /** Builds a {@code stock} command; pass {@code null} to leave out the name or the threshold. */
    private static Command stock(String inventoryName, String below) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (inventoryName != null) {
            args.put(ArgKey.INV_NAME, inventoryName);
        }
        if (below != null) {
            args.put(ArgKey.STOCK_BELOW, below);
        }
        return new Command(CommandType.STOCK, args);
    }

    /** Creates "Shop" with three items, then leaves it so the user is outside all inventories. */
    private void setUpStockedShop() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);
        Processor.process(itemAdd("Rice", "Food", "10.5"), state);
        Processor.process(itemAdd("Pen", null, null), state);
        state.leaveInventory();
    }

    @Test
    public void process_stockNamedInventoryFromOutside_showsItemsAndTotals() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", null), state);

        assertEquals("Stock levels in Shop:\n1. A4 Paper Case (Stationery) x2\n2. Rice (Food) x10.5\n3. Pen x1"
                + "\nTotal: 3 item(s), 13.5 unit(s)", result.message());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_stockWithoutNameInside_usesCurrentInventory() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("Pen", null, "3"), state);

        Result result = Processor.process(stock(null, null), state);

        assertEquals("Stock levels in Shop:\n1. Pen x3\nTotal: 1 item(s), 3 unit(s)", result.message());
    }

    @Test
    public void process_stockNamedOtherInventoryInside_showsNamedInventoryAndStaysInside() throws StockHolmException {
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Box", "", 4));
        inventories.add(warehouse);
        Inventory shop = enterNewShop();

        Result result = Processor.process(stock("Warehouse", null), state);

        assertEquals("Stock levels in Warehouse:\n1. Box x4\nTotal: 1 item(s), 4 unit(s)", result.message());
        assertSame(shop, state.getCurrentInventory());
    }

    @Test
    public void process_stockDecimalCounts_totalHasNoRoundingError() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A", null, "0.1"), state);
        Processor.process(itemAdd("B", null, "0.2"), state);

        Result result = Processor.process(stock(null, null), state);

        assertTrue(result.message().endsWith("Total: 2 item(s), 0.3 unit(s)"), result.message());
    }

    @Test
    public void process_stockEmptyInventory_returnsNoItemsMessage() throws StockHolmException {
        inventories.add(new Inventory("Shop"));
        Result result = Processor.process(stock("Shop", null), state);
        assertEquals("No items in Shop yet.", result.message());
    }

    @Test
    public void process_stockWithoutNameOutside_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(stock(null, null), state));
        assertTrue(e.getMessage().startsWith("Missing inventory name."));
    }

    @Test
    public void process_stockMissingInventory_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(stock("Ghost", null), state));
        assertEquals("No such inventory: Ghost", e.getMessage());
    }

    @Test
    public void process_stockBelow_showsOnlyLowItemsWithOriginalNumbers() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "5"), state);

        assertEquals("Items in Shop below 5:\n1. A4 Paper Case (Stationery) x2\n3. Pen x1", result.message());
    }

    @Test
    public void process_stockBelowEqualCount_excludesThatItem() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "2"), state);

        assertEquals("Items in Shop below 2:\n3. Pen x1", result.message());
    }

    @Test
    public void process_stockBelowNoneLow_returnsNoItemsBelowMessage() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "0.5"), state);

        assertEquals("No items in Shop below 0.5.", result.message());
    }
}
