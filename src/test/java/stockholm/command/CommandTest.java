package stockholm.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Command} record, including its convenience constructor and argument access.
 */
class CommandTest {
    @Test
    public void constructor_typeAndArgs_storesBoth() {
        Command command = new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "Shop"));
        assertEquals(CommandType.INV_ADD, command.type());
        assertEquals(Map.of(ArgKey.INV_NAME, "Shop"), command.args());
    }

    @Test
    public void constructor_typeOnly_hasNoArgs() {
        Command command = new Command(CommandType.QUIT);
        assertEquals(CommandType.QUIT, command.type());
        assertTrue(command.args().isEmpty());
    }

    @Test
    public void constructor_callerMapChangedLater_commandUnaffected() {
        Map<ArgKey, String> args = new HashMap<>();
        args.put(ArgKey.INV_NAME, "Shop");
        Command command = new Command(CommandType.INV_ADD, args);

        args.put(ArgKey.INV_NAME, "Changed");

        assertEquals("Shop", command.getArg(ArgKey.INV_NAME));
    }

    @Test
    public void args_modified_throwsException() {
        Command command = new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "Shop"));
        assertThrows(UnsupportedOperationException.class, () -> command.args().clear());
    }

    @Test
    public void getArg_present_returnsValue() {
        Command command = new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "Shop"));
        assertEquals("Shop", command.getArg(ArgKey.INV_NAME));
    }

    @Test
    public void getArg_missing_throwsIllegalState() {
        Command command = new Command(CommandType.QUIT);
        assertThrows(IllegalStateException.class, () -> command.getArg(ArgKey.INV_NAME));
    }

    @Test
    public void hasArg_presentAndMissing_returnsCorrectly() {
        assertTrue(new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "A")).hasArg(ArgKey.INV_NAME));
        assertFalse(new Command(CommandType.QUIT).hasArg(ArgKey.INV_NAME));
    }

    @Test
    public void equals_sameValues_areEqual() {
        assertEquals(new Command(CommandType.QUIT), new Command(CommandType.QUIT, Map.of()));
        assertEquals(new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "A")),
                new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "A")));
    }

    @Test
    public void equals_differentValues_areNotEqual() {
        assertNotEquals(new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "A")),
                new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "B")));
        assertNotEquals(new Command(CommandType.INV_ADD, Map.of(ArgKey.INV_NAME, "A")),
                new Command(CommandType.INV_DELETE, Map.of(ArgKey.INV_NAME, "A")));
    }
}
