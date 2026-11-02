package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests the {@code inv}, {@code enter} and {@code back} commands through {@link Processor#process(Command, AppState)},
 * using commands built by {@link stockholm.parser.Parser} or directly.
 */
class InventoryCommandsTest extends ProcessorTestBase {
    @Test
    public void process_quotedInventoryNames_remainAccessibleAcrossCommands() throws StockHolmException {
        Processor.process(Parser.parseCommand("inv add \"  New Shop  \""), state);
        Inventory shop = state.findInventory("New Shop");
        assertEquals("New Shop", shop.getName());
        Processor.process(Parser.parseCommand("enter \" New Shop \""), state);
        assertSame(shop, state.getCurrentInventory());
        Processor.process(Parser.parseCommand("item add Pen --count=3"), state);
        Processor.process(Parser.parseCommand("item min 1 5"), state);
        Processor.process(Parser.parseCommand("back"), state);

        for (String name : new String[]{"New Shop", "\"New Shop\"", "\"  New Shop  \""}) {
            assertTrue(Processor.process(Parser.parseCommand("stock " + name), state).message()
                    .contains("Pen x3"));
            assertTrue(Processor.process(Parser.parseCommand("restock " + name), state).message()
                    .contains("reorder 2"));
        }

        Processor.process(Parser.parseCommand("inv add \" Warehouse \""), state);
        Processor.process(Parser.parseCommand(
                "transfer 1 --from=\" New Shop \" --to=\" Warehouse \" --count=1"), state);
        Order transfer = orders().getFirst();
        assertEquals("New Shop", transfer.getSrc());
        assertEquals("Warehouse", transfer.getDst());
        for (String option : new String[]{"inv", "inventory"}) {
            for (String name : new String[]{"New Shop", "Warehouse"}) {
                assertTrue(Processor.process(Parser.parseCommand("order list --" + option + "=\" " + name + " \""),
                        state).message().contains("Transfer order (from New Shop to Warehouse)"));
            }
        }

        Processor.process(Parser.parseCommand("inv rename \" New Shop \" \"  Central Shop  \""), state);
        assertSame(shop, state.findInventory("Central Shop"));
        assertEquals("Central Shop", transfer.getSrc());
        Processor.process(Parser.parseCommand("enter Central Shop"), state);
        assertSame(shop, state.getCurrentInventory());
        Processor.process(Parser.parseCommand("back"), state);
        Processor.process(Parser.parseCommand("inv delete \" Central Shop \""), state);
        assertNull(state.findInventory("Central Shop"));
        assertEquals(OrderState.CANCELED, transfer.getState());
    }

    @Test
    public void process_quotedInventoryDuplicates_rejectsNormalizedNames() throws StockHolmException {
        Processor.process(Parser.parseCommand("inv add New Shop"), state);
        Processor.process(Parser.parseCommand("inv add Warehouse"), state);
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv add \" New Shop \""), state));
        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv rename Warehouse \" New Shop \""), state));
        assertEquals(2, inventories.size());
        assertEquals("Warehouse", inventories.get(1).getName());
    }

    @Test
    public void process_renameWithUnmatchedClosingQuote_keepsInventoryAndOrders() throws StockHolmException {
        Processor.process(Parser.parseCommand("inv add x"), state);
        Inventory inventory = state.findInventory("x");
        Order order = new ImportOrder(new Item("Pen", "", 1), inventory);
        state.getOrderHistory().add(order);

        assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("inv rename x y y \""), state));

        assertEquals(1, inventories.size());
        assertSame(inventory, state.findInventory("x"));
        assertEquals("x", order.getDst());
        Processor.process(Parser.parseCommand("inv rename x \" y y \""), state);
        assertSame(inventory, state.findInventory("y y"));
        assertEquals("y y", order.getDst());
    }

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
}
