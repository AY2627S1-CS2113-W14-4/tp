package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;

/**
 * Tests for {@link Processor#process(Command, ArrayList)}.
 * Each test gets a fresh, empty inventory list so tests cannot affect one another.
 */
class ProcessorTest {
    private ArrayList<Inventory> inventories;

    @BeforeEach
    public void setUp() {
        inventories = new ArrayList<>();
    }

    @Test
    public void process_noOp_returnsEmptyNonExitResult() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.NO_OP), inventories);
        assertEquals(new Result("", false), result);
    }

    @Test
    public void process_quit_returnsExitResult() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.QUIT), inventories);
        assertTrue(result.isExit());
        assertEquals("", result.message());
    }

    @Test
    public void process_invAdd_addsInventory() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.INV_ADD, "Shop"), inventories);

        assertEquals("Added inventory: Shop", result.message());
        assertFalse(result.isExit());
        assertEquals(1, inventories.size());
        assertEquals("Shop", inventories.get(0).getName());
    }

    @Test
    public void process_invAddDuplicate_throwsException() throws StockHolmException {
        Processor.process(new Command(CommandType.INV_ADD, "Shop"), inventories);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(new Command(CommandType.INV_ADD, "Shop"), inventories));
        assertEquals("Inventory already exists: Shop", e.getMessage());
        assertEquals(1, inventories.size());
    }

    @Test
    public void process_invDeleteExisting_removesInventory() throws StockHolmException {
        Processor.process(new Command(CommandType.INV_ADD, "Shop"), inventories);
        Processor.process(new Command(CommandType.INV_ADD, "Warehouse"), inventories);

        Result result = Processor.process(new Command(CommandType.INV_DELETE, "Shop"), inventories);

        assertEquals("Deleted inventory: Shop", result.message());
        assertEquals(1, inventories.size());
        assertEquals("Warehouse", inventories.get(0).getName());
    }

    @Test
    public void process_invDeleteMissing_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(new Command(CommandType.INV_DELETE, "Ghost"), inventories));
        assertEquals("No such inventory: Ghost", e.getMessage());
    }

    @Test
    public void process_invListEmpty_returnsNoInventoriesMessage() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.INV_LIST), inventories);
        assertEquals(new Result("No inventories yet.", false), result);
    }

    @Test
    public void process_invListNonEmpty_returnsNumberedList() throws StockHolmException {
        inventories.add(new Inventory("Shop"));
        inventories.add(new Inventory("Warehouse"));

        Result result = Processor.process(new Command(CommandType.INV_LIST), inventories);

        assertEquals("Inventories:\n1. Shop\n2. Warehouse", result.message());
        assertFalse(result.isExit());
    }

    @Test
    public void process_unimplementedCommands_throwException() {
        // ENTER and BACK exist in CommandType but are not handled by the Processor yet.
        for (CommandType type : new CommandType[]{CommandType.ENTER, CommandType.BACK}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(new Command(type), inventories));
            assertEquals("Command not supported yet: " + type, e.getMessage());
        }
    }
}
