package stockholm.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
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
        ImportOrder pen = new ImportOrder(new Item("Pen", "", 1), inventory);
        ImportOrder paper = new ImportOrder(new Item("Paper", "", 2), inventory);

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
                () -> inventory.getOrders().add(new ImportOrder(new Item("Pen", "", 1), inventory)));
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

    @Test
    public void checkCanMerge_noItemWithThatName_returnsTrue() {
        Inventory inventory = new Inventory("Shop");
        inventory.addItem(new Item("Pen", "Stationery", 1));
        assertTrue(inventory.checkCanMerge(new Item("Rice", "Food", 1)));
    }

    @Test
    public void checkCanMerge_sameOrEmptyType_returnsTrue() {
        Inventory inventory = new Inventory("Shop");
        inventory.addItem(new Item("Pen", "Stationery", 1));
        assertTrue(inventory.checkCanMerge(new Item("Pen", "Stationery", 1)));
        assertTrue(inventory.checkCanMerge(new Item("Pen", "", 1)));
    }

    @Test
    public void checkCanMerge_differentType_returnsFalse() {
        Inventory inventory = new Inventory("Shop");
        inventory.addItem(new Item("Pen", "Stationery", 1));
        assertFalse(inventory.checkCanMerge(new Item("Pen", "Office", 1)));
    }

    @Test
    public void addOrMerge_newName_addsItemAndReturnsIt() throws StockHolmException {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 2);
        assertSame(pen, inventory.addOrMerge(pen));
        assertEquals(1, inventory.getItems().size());
        assertSame(pen, inventory.getItems().get(0));
    }

    @Test
    public void addOrMerge_existingName_addsCountAndReturnsExistingItem() throws StockHolmException {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 2);
        inventory.addItem(pen);

        Item more = new Item("Pen", "", 1.5);
        assertSame(pen, inventory.addOrMerge(more));
        assertEquals(1, inventory.getItems().size());
        assertEquals(3.5, pen.getCount());
        assertEquals("Stationery", pen.getType());
        assertEquals(1.5, more.getCount());
    }

    @Test
    public void addOrMerge_differentType_throwsAndChangesNothing() {
        Inventory inventory = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 2);
        inventory.addItem(pen);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> inventory.addOrMerge(new Item("Pen", "Office", 1)));
        assertEquals("Item already exists with a different type: Pen (Stationery) x2.", e.getMessage());
        assertEquals(1, inventory.getItems().size());
        assertEquals(2, pen.getCount());
    }
}
