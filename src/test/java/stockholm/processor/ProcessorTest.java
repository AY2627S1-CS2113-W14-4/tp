package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Builds an {@code inv} command that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
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
    public void process_unimplementedCommands_throwException() {
        // ENTER and BACK exist in CommandType but are not handled by the Processor yet.
        for (CommandType type : new CommandType[]{CommandType.ENTER, CommandType.BACK}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(new Command(type), state));
            assertEquals("Command not supported yet: " + type, e.getMessage());
        }
    }
}
