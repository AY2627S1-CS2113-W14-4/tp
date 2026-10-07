package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
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

    /** Creates an inventory named "Shop" and enters it. */
    private Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }

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
}
