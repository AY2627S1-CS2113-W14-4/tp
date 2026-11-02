package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.command.Location;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.state.AppState;

/**
 * Tests command dispatch, {@code help} and {@code quit} through
 * {@link Processor#process(Command, AppState)}.
 */
class ProcessorTest extends ProcessorTestBase {
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
    public void process_helpOutsideInventory_listsEveryCommand() throws StockHolmException {
        Result result = Processor.process(new Command(CommandType.HELP), state);
        assertFalse(result.isExit());
        String[] commands = {"help", "quit", "enter NAME", "stock", "inv add", "inv rename", "inv delete",
            "inv list", "back",
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
}
