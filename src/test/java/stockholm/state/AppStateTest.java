package stockholm.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.inventory.Inventory;

/**
 * Tests for {@link AppState}.
 */
class AppStateTest {
    @Test
    public void newState_isEmptyAndOutsideInventory() {
        AppState state = new AppState();
        assertTrue(state.getInventories().isEmpty());
        assertFalse(state.isInsideInventory());
        assertNull(state.getCurrentInventory());
    }

    @Test
    public void enterInventory_setsCurrentInventory() {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");

        state.enterInventory(shop);

        assertTrue(state.isInsideInventory());
        assertSame(shop, state.getCurrentInventory());
    }

    @Test
    public void leaveInventory_clearsCurrentInventory() {
        AppState state = new AppState();
        state.enterInventory(new Inventory("Shop"));

        state.leaveInventory();

        assertFalse(state.isInsideInventory());
        assertNull(state.getCurrentInventory());
    }
}
