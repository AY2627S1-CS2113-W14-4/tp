package stockholm.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Command} record, including its convenience constructor.
 */
class CommandTest {
    @Test
    public void constructor_typeAndName_storesBoth() {
        Command command = new Command(CommandType.INV_ADD, "Shop");
        assertEquals(CommandType.INV_ADD, command.type());
        assertEquals("Shop", command.inventoryName());
    }

    @Test
    public void constructor_typeOnly_hasNullInventoryName() {
        Command command = new Command(CommandType.QUIT);
        assertEquals(CommandType.QUIT, command.type());
        assertNull(command.inventoryName());
    }

    @Test
    public void equals_sameValues_areEqual() {
        assertEquals(new Command(CommandType.QUIT), new Command(CommandType.QUIT, null));
        assertEquals(new Command(CommandType.INV_ADD, "A"), new Command(CommandType.INV_ADD, "A"));
    }

    @Test
    public void equals_differentValues_areNotEqual() {
        assertNotEquals(new Command(CommandType.INV_ADD, "A"), new Command(CommandType.INV_ADD, "B"));
        assertNotEquals(new Command(CommandType.INV_ADD, "A"), new Command(CommandType.INV_DELETE, "A"));
    }
}
