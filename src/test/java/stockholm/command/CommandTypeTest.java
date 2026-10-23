package stockholm.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CommandType}.
 */
class CommandTypeTest {
    @Test
    public void usageAndDescription_everyCommandExceptNoOp_present() {
        for (CommandType type : CommandType.values()) {
            if (type == CommandType.NO_OP) {
                continue;
            }
            assertNotNull(type.location(), type + " needs a location");
            assertFalse(type.usage() == null || type.usage().isBlank(), type + " needs a usage");
            assertFalse(type.description() == null || type.description().isBlank(), type + " needs a description");
        }
    }

    @Test
    public void noOp_hasNoUsageAndWorksAnywhere() {
        assertNull(CommandType.NO_OP.usage());
        assertEquals(Location.ANYWHERE, CommandType.NO_OP.location());
    }

    @Test
    public void location_matchesWhereCommandsAreAllowed() {
        assertEquals(Location.OUTSIDE, CommandType.INV_ADD.location());
        assertEquals(Location.INSIDE, CommandType.BACK.location());
        assertEquals(Location.INSIDE, CommandType.ITEM_ADD.location());
        assertEquals(Location.ANYWHERE, CommandType.ENTER.location());
        assertEquals(Location.ANYWHERE, CommandType.STOCK.location());
    }
}
