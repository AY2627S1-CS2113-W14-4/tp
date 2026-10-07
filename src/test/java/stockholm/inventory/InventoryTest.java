package stockholm.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Inventory}.
 */
class InventoryTest {
    @Test
    public void getName_returnsConstructorName() {
        assertEquals("Shop", new Inventory("Shop").getName());
    }

    @Test
    public void addItem_validItem_doesNotThrow() {
        // Inventory has no getter for its items yet, so we can only check that adding succeeds.
        // Strengthen this test once a getItems()/size() method exists.
        Inventory inventory = new Inventory("Shop");
        assertDoesNotThrow(() -> inventory.addItem(new Item()));
    }
}
