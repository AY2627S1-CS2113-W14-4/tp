package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.command.Location;
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

    /** Shortcut to every order in {@code state}'s order history. */
    private List<Order> orders() {
        return state.getOrderHistory().getOrders();
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

    // ---------- help ----------

    @Test
    public void process_helpOutsideInventory_listsEveryCommand() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.HELP), state);
        assertFalse(result.isExit());
        String[] commands = {"help", "quit", "enter NAME", "stock", "inv add", "inv delete", "inv list", "back",
            "item add", "item list", "item delete", "import", "export", "order list", "approve", "deliver"};
        for (String command : commands) {
            assertTrue(result.message().contains(command), "help should mention " + command);
        }
        assertTrue(inventories.isEmpty());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_helpInsideInventory_showsSameHelpAndStaysInside() throws StockHolmException {
        Result outside = Processor.process(new Command(CommandType.HELP), state);
        Inventory shop = enterNewShop();
        Result inside = Processor.process(new Command(CommandType.HELP), state);
        assertEquals(outside, inside);
        assertSame(shop, state.getCurrentInventory());
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
    public void process_invDeleteWithOrders_cancelsOnlyItsOpenOrders() throws StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 5));
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(Parser.parseCommand("export 1 --count=2"), state);
        Processor.process(Parser.parseCommand("approve 2"), state);
        Processor.process(Parser.parseCommand("import Ink"), state);
        Processor.process(Parser.parseCommand("approve 3"), state);
        Processor.process(Parser.parseCommand("deliver 3"), state);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);
        Processor.process(Parser.parseCommand("import Box"), state);
        state.leaveInventory();

        Result result = Processor.process(invCommand(CommandType.INV_DELETE, "Shop"), state);

        assertEquals("Deleted inventory: Shop\nCanceled 2 open order(s) of Shop.", result.message());
        assertEquals(OrderState.CANCELED, orders().get(0).getState());
        assertEquals(OrderState.CANCELED, orders().get(1).getState());
        assertEquals(OrderState.DELIVERED, orders().get(2).getState());
        assertEquals(OrderState.WAITING_APPROVAL, orders().get(3).getState());
        assertEquals(List.of(warehouse), inventories);
    }

    @Test
    public void process_invAddReusedName_doesNotTakeOverOldOrders() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen --count=5"), state);
        state.leaveInventory();
        Processor.process(invCommand(CommandType.INV_DELETE, "Shop"), state);
        Processor.process(invCommand(CommandType.INV_ADD, "Shop"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("approve 1"), state));

        assertEquals("Order is not waiting for approval (current state: CANCELED).", e.getMessage());
        assertTrue(state.findInventory("Shop").getItems().isEmpty());
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
    public void process_itemAddDecimalMerge_showsExactCount() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("Rice", null, "0.1"), state);

        Result result = Processor.process(itemAdd("Rice", null, "0.2"), state);

        assertEquals("Added 0.2 to existing item: Rice x0.3", result.message());
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

        assertEquals("Item already exists with a different type: Rice (Food) x1.", e.getMessage());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    // ---------- approve ----------

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

    // ---------- reject ----------

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

    // ---------- cancel ----------

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

    // ---------- deliver ----------

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

    // ---------- import ----------

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

    // ---------- export ----------

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

        assertEquals(new Result("Orders:"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: \n   Item count: 1"
                + "\n2. Export order (from Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5", false), result);
        assertEquals(2.5, shop.getItems().get(0).getCount());
        assertEquals(2, orders().size());
    }

    // ---------- order list ----------

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

        assertEquals(new Result("Orders:"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: Food\n   Item count: 2.5"
                + "\n2. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: \n   Item count: 1", false), result);
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

        assertEquals(new Result("Orders:"
                + "\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Rice\n   Item type: \n   Item count: 1"
                + "\n2. Export order (from Warehouse)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: Stationery\n   Item count: 3", false), result);
    }

    @Test
    public void process_orderListOutsideInventory_listsEveryOrder() throws StockHolmException {
        enterNewShop();
        Processor.process(Parser.parseCommand("import Pen"), state);
        Processor.process(new Command(CommandType.BACK), state);

        Result result = Processor.process(Parser.parseCommand("order list"), state);

        assertEquals(new Result("Orders:\n1. Import order (to Shop)\n   State: WAITING_APPROVAL"
                + "\n   Item name: Pen\n   Item type: \n   Item count: 1", false), result);
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

    @Test
    public void process_stockItemWithNonPositiveCount_failsAssertion() {
        // Assertions are enabled in Gradle tests. Such an item can only appear through a bug, since the Parser
        // and Storage both reject non-positive counts; the assertion makes that bug visible instead of
        // printing a wrong total.
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Broken", "", 0));
        inventories.add(shop);

        assertThrows(AssertionError.class, () -> Processor.process(stock("Shop", null), state));
    }

    // ---------- dispatch ----------

    @Test
    public void process_everyCommandType_hasHandler() {
        Map<ArgKey, String> allArgs = new EnumMap<>(ArgKey.class);
        for (ArgKey key : ArgKey.values()) {
            allArgs.put(key, "1");
        }
        for (CommandType type : CommandType.values()) {
            setUp();
            if (type.location() != Location.OUTSIDE) {
                enterNewShop();
            }
            try {
                Processor.process(new Command(type, allArgs), state);
            } catch (StockHolmException e) {
                // Business-rule errors are fine here; only a missing handler is a failure.
                assertFalse(e.getMessage().startsWith("Command not supported yet"), type + " has no handler");
            }
        }
    }

    // ---------- find ----------

    /** Builds a {@code find} command; pass {@code null} to leave out the keyword or the type. */
    private static Command find(String keyword, String type) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (keyword != null) {
            args.put(ArgKey.FIND_KEYWORD, keyword);
        }
        if (type != null) {
            args.put(ArgKey.ITEM_TYPE, type);
        }
        return new Command(CommandType.FIND, args);
    }

    /** Creates "Shop" (pens and rice) and "Warehouse" (a pen), leaving the user outside both. */
    private void setUpTwoInventoriesWithPens() {
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Blue Pen", "Stationery", 3));
        shop.addItem(new Item("Rice", "Food", 2.5));
        shop.addItem(new Item("Pen Refill", "stationery", 10));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "", 100));
        inventories.add(shop);
        inventories.add(warehouse);
    }

    @Test
    public void process_findOutsideInventory_searchesAllInventoriesIgnoringCase() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find("PEN", null), state);

        assertEquals("Found 3 item(s) matching \"PEN\":\nShop:\n  1. Blue Pen (Stationery) x3"
                + "\n  3. Pen Refill (stationery) x10\nWarehouse:\n  1. Pen x100", result.message());
    }

    @Test
    public void process_findInsideInventory_searchesOnlyCurrentInventory() throws StockHolmException {
        setUpTwoInventoriesWithPens();
        state.enterInventory(state.findInventory("Warehouse"));

        Result result = Processor.process(find("pen", null), state);

        assertEquals("Found 1 item(s) matching \"pen\":\nWarehouse:\n  1. Pen x100", result.message());
    }

    @Test
    public void process_findByType_matchesWholeTypeIgnoringCase() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find(null, "STATIONERY"), state);

        assertEquals("Found 2 item(s) of type STATIONERY:\nShop:\n  1. Blue Pen (Stationery) x3"
                + "\n  3. Pen Refill (stationery) x10", result.message());
        assertEquals("No items of type Station.",
                Processor.process(find(null, "Station"), state).message());
    }

    @Test
    public void process_findKeywordAndType_requiresBothToMatch() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find("refill", "Stationery"), state);

        assertEquals("Found 1 item(s) matching \"refill\" of type Stationery:\nShop:\n  3. Pen Refill (stationery) x10",
                result.message());
    }

    @Test
    public void process_findNoMatches_returnsNoItemsMessage() throws StockHolmException {
        setUpTwoInventoriesWithPens();
        assertEquals("No items matching \"chair\".", Processor.process(find("chair", null), state).message());
    }

    @Test
    public void process_findWithNoInventories_returnsNoItemsMessage() throws StockHolmException {
        assertEquals("No items matching \"pen\".", Processor.process(find("pen", null), state).message());
    }

    @Test
    public void process_find_doesNotChangeState() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Processor.process(find("pen", null), state);

        assertFalse(state.isInsideInventory());
        assertEquals(3, state.findInventory("Shop").getItems().size());
    }

    // ---------- item min / restock / low-stock alerts ----------

    /** Enters a new "Shop" holding Pen x3 and Rice x10, and returns it. */
    private Inventory enterShopWithPenAndRice() {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Pen", "Stationery", 3));
        shop.addItem(new Item("Rice", "Food", 10));
        return shop;
    }

    @Test
    public void process_itemMinAboveCount_setsMinimumAndAlerts() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();

        Result result = Processor.process(Parser.parseCommand("item min 1 5"), state);

        assertEquals("Set the minimum stock of Pen to 5.\n"
                + "Low stock: Pen has 3 left, below its minimum of 5. Reorder at least 2.", result.message());
        assertEquals(5, shop.getItems().get(0).getMinCount());
    }

    @Test
    public void process_itemMinBelowCount_setsMinimumWithoutAlert() throws StockHolmException {
        enterShopWithPenAndRice();
        Result result = Processor.process(Parser.parseCommand("item min 2 4.5"), state);
        assertEquals("Set the minimum stock of Rice to 4.5.", result.message());
    }

    @Test
    public void process_itemMinZero_clearsMinimum() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);

        Result result = Processor.process(Parser.parseCommand("item min 1 0"), state);

        assertEquals("Cleared the minimum stock of Pen.", result.message());
        assertEquals(0, shop.getItems().get(0).getMinCount());
    }

    @Test
    public void process_itemMinIndexTooLarge_throwsException() {
        enterShopWithPenAndRice();
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("item min 3 5"), state));
        assertEquals("No item number 3. Shop has 2 item(s); use item list to see them.", e.getMessage());
    }

    @Test
    public void process_itemMinOutsideInventory_throwsException() {
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("item min 1 5"), state));
    }

    @Test
    public void process_exportBelowMinimum_alertsLowStock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(8);

        Result result = Processor.process(Parser.parseCommand("export 2 --count=4"), state);

        assertTrue(result.message().endsWith(
                "\nLow stock: Rice has 6 left, below its minimum of 8. Reorder at least 2."), result.message());
    }

    @Test
    public void process_exportStillAboveMinimum_hasNoAlert() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(5);

        Result result = Processor.process(Parser.parseCommand("export 2 --count=4"), state);

        assertFalse(result.message().contains("Low stock"), result.message());
    }

    @Test
    public void process_exportAllWithMinimum_alertsOutOfStock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(2);

        Result result = Processor.process(Parser.parseCommand("export 1"), state);

        assertTrue(result.message().endsWith(
                "\nOut of stock: Pen has run out and was removed, along with its minimum of 2."), result.message());
        assertEquals(1, shop.getItems().size());
    }

    @Test
    public void process_stockWithLowItem_marksIt() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);

        Result result = Processor.process(stock(null, null), state);

        assertEquals("Stock levels in Shop:\n1. Pen (Stationery) x3 [LOW: min 5]\n2. Rice (Food) x10"
                + "\nTotal: 2 item(s), 13 unit(s)", result.message());
    }

    @Test
    public void process_restock_listsOnlyLowItemsWithReorderAmounts() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);
        shop.getItems().get(1).setMinCount(10);
        state.leaveInventory();

        Result result = Processor.process(Parser.parseCommand("restock Shop"), state);

        assertEquals("Items to restock in Shop:\n1. Pen (Stationery) x3 (min 5, reorder 2)", result.message());
    }

    @Test
    public void process_restockNoneLow_returnsNothingToRestock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(5);
        assertEquals("Nothing to restock in Shop.", Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockNoMinimums_explainsHowToSetOne() throws StockHolmException {
        enterShopWithPenAndRice();
        assertEquals("No items in Shop have a minimum stock level. Set one with item min INDEX COUNT.",
                Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockOutsideWithoutName_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("restock"), state));
        assertEquals("Missing inventory name. Usage: restock [NAME], or enter an inventory first.", e.getMessage());
    }
}
