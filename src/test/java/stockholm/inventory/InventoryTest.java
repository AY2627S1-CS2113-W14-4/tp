package stockholm.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.order.ImportOrder;

/**
 * Tests for {@link Inventory}.
 */
class InventoryTest {
    @Test
    public void getName_returnsConstructorName() {
        assertEquals("Shop", new Inventory("Shop").getName());
    }

    @Test
    public void getItems_newInventory_isEmpty() {
        assertTrue(new Inventory("Shop").getItems().isEmpty());
    }

    @Test
    public void getOrders_newInventory_isEmpty() {
        assertTrue(new Inventory("Shop").getOrders().isEmpty());
    }

    @Test
    public void addOrder_twoOrders_keptInInsertionOrderWithoutChangingStock() {
        Inventory inventory = new Inventory("Shop");
        ImportOrder pen = new ImportOrder(new Item("Pen", "", 1));
        ImportOrder paper = new ImportOrder(new Item("Paper", "", 2));

        inventory.addOrder(pen);
        inventory.addOrder(paper);

        assertEquals(2, inventory.getOrders().size());
        assertSame(pen, inventory.getOrders().get(0));
        assertSame(paper, inventory.getOrders().get(1));
        assertTrue(inventory.getItems().isEmpty());
    }

    @Test
    public void getOrders_modified_throwsException() {
        Inventory inventory = new Inventory("Shop");
        assertThrows(UnsupportedOperationException.class,
                () -> inventory.getOrders().add(new ImportOrder(new Item("Pen", "", 1))));
    }

    @Test
    public void addItem_twoItems_keptInInsertionOrder() {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "", 1);
        Item paper = new Item("Paper", "", 1);

        inventory.addItem(pen);
        inventory.addItem(paper);

        assertEquals(2, inventory.getItems().size());
        assertSame(pen, inventory.getItems().get(0));
        assertSame(paper, inventory.getItems().get(1));
    }

    @Test
    public void getItems_modified_throwsException() {
        Inventory inventory = new Inventory("Shop");
        assertThrows(UnsupportedOperationException.class,
                () -> inventory.getItems().add(new Item("Pen", "", 1)));
    }

    @Test
    public void findItem_existingName_returnsItem() {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "", 1);
        inventory.addItem(pen);

        assertSame(pen, inventory.findItem("Pen"));
    }

    @Test
    public void findItem_missingOrDifferentCase_returnsNull() {
        Inventory inventory = new Inventory("Shop");
        inventory.addItem(new Item("Pen", "", 1));

        assertNull(inventory.findItem("Paper"));
        assertNull(inventory.findItem("pen"));
    }

    @Test
    public void removeItem_validIndex_removesAndReturnsItem() {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "", 1);
        Item paper = new Item("Paper", "", 1);
        inventory.addItem(pen);
        inventory.addItem(paper);

        assertSame(pen, inventory.removeItem(0));
        assertEquals(1, inventory.getItems().size());
        assertSame(paper, inventory.getItems().get(0));
    }

    @Test
    public void removeItem_invalidIndex_throwsException() {
        Inventory inventory = new Inventory("Shop");
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.removeItem(0));
    }
}
