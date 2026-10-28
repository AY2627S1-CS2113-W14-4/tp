package stockholm.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

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
        assertEquals(Location.ANYWHERE, CommandType.APPROVE.location());
        assertEquals(Location.ANYWHERE, CommandType.REJECT.location());
        assertEquals(Location.ANYWHERE, CommandType.CANCEL.location());
        assertEquals(Location.ANYWHERE, CommandType.DELIVER.location());
        assertEquals(Location.ANYWHERE, CommandType.ENTER.location());
        assertEquals(Location.ANYWHERE, CommandType.STOCK.location());
        assertEquals(Location.ANYWHERE, CommandType.TRANSFER.location());
    }

    @Test
    public void changes_matchWhatEachCommandModifies() {
        Set<CommandType> changeInventories = EnumSet.of(CommandType.INV_ADD, CommandType.INV_DELETE,
                CommandType.ITEM_ADD, CommandType.ITEM_DELETE, CommandType.EXPORT, CommandType.DELIVER,
                CommandType.REJECT, CommandType.CANCEL, CommandType.ITEM_MIN, CommandType.TRANSFER);
        Set<CommandType> changeOrders = EnumSet.of(CommandType.IMPORT, CommandType.EXPORT, CommandType.APPROVE,
                CommandType.REJECT, CommandType.CANCEL, CommandType.DELIVER, CommandType.INV_DELETE,
                CommandType.TRANSFER);
        for (CommandType type : CommandType.values()) {
            assertNotNull(type.changes(), type + " needs a Changes value");
            assertEquals(changeInventories.contains(type), type.changes().includesInventories(), type.toString());
            assertEquals(changeOrders.contains(type), type.changes().includesOrders(), type.toString());
        }
    }

    @Test
    public void changes_eachValue_reportsTheRightFiles() {
        assertFalse(Changes.NONE.includesInventories() || Changes.NONE.includesOrders());
        assertTrue(Changes.INVENTORIES.includesInventories() && !Changes.INVENTORIES.includesOrders());
        assertTrue(!Changes.ORDERS.includesInventories() && Changes.ORDERS.includesOrders());
        assertTrue(Changes.INVENTORIES_AND_ORDERS.includesInventories()
                && Changes.INVENTORIES_AND_ORDERS.includesOrders());
    }
}
