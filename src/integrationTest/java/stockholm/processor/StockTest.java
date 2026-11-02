package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/**
 * Tests the {@code stock} command through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class StockTest extends ProcessorTestBase {
    @Test
    public void process_stockNamedInventoryFromOutside_showsItemsAndTotals() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", null), state);

        assertEquals("Stock levels in Shop:\n1. A4 Paper Case (Stationery) x2\n2. Rice (Food) x10.5\n3. Pen x1"
                + "\nTotal: 3 item(s), 13.5 unit(s)", result.message());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_stockWithoutNameInside_usesCurrentInventory() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("Pen", null, "3"), state);

        Result result = Processor.process(stock(null, null), state);

        assertEquals("Stock levels in Shop:\n1. Pen x3\nTotal: 1 item(s), 3 unit(s)", result.message());
    }

    @Test
    public void process_stockNamedOtherInventoryInside_showsNamedInventoryAndStaysInside() throws StockHolmException {
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Box", "", 4));
        inventories.add(warehouse);
        Inventory shop = enterNewShop();

        Result result = Processor.process(stock("Warehouse", null), state);

        assertEquals("Stock levels in Warehouse:\n1. Box x4\nTotal: 1 item(s), 4 unit(s)", result.message());
        assertSame(shop, state.getCurrentInventory());
    }

    @Test
    public void process_stockDecimalCounts_totalHasNoRoundingError() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A", null, "0.1"), state);
        Processor.process(itemAdd("B", null, "0.2"), state);

        Result result = Processor.process(stock(null, null), state);

        assertTrue(result.message().endsWith("Total: 2 item(s), 0.3 unit(s)"), result.message());
    }

    @Test
    public void process_stockEmptyInventory_returnsNoItemsMessage() throws StockHolmException {
        inventories.add(new Inventory("Shop"));
        Result result = Processor.process(stock("Shop", null), state);
        assertEquals("No items in Shop yet.", result.message());
    }

    @Test
    public void process_stockWithoutNameOutside_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(stock(null, null), state));
        assertTrue(e.getMessage().startsWith("Missing inventory name."));
    }

    @Test
    public void process_stockMissingInventory_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(stock("Ghost", null), state));
        assertEquals("No such inventory: Ghost", e.getMessage());
    }

    @Test
    public void process_stockBelow_showsOnlyLowItemsWithOriginalNumbers() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "5"), state);

        assertEquals("Items in Shop below 5:\n1. A4 Paper Case (Stationery) x2\n3. Pen x1", result.message());
    }

    @Test
    public void process_stockBelowEqualCount_excludesThatItem() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "2"), state);

        assertEquals("Items in Shop below 2:\n3. Pen x1", result.message());
    }

    @Test
    public void process_stockBelowNoneLow_returnsNoItemsBelowMessage() throws StockHolmException {
        setUpStockedShop();

        Result result = Processor.process(stock("Shop", "0.5"), state);

        assertEquals("No items in Shop below 0.5.", result.message());
    }

    @Test
    public void process_stockItemWithNonPositiveCount_failsAssertion() {
        // Assertions are enabled in Gradle tests. Such an item can only appear through a bug, since the Parser
        // and Storage both reject non-positive counts; the assertion makes that bug visible instead of
        // printing a wrong total.
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Broken", "", 0));
        inventories.add(shop);

        assertThrows(AssertionError.class, () -> Processor.process(stock("Shop", null), state));
    }

    /** Creates "Shop" with three items, then leaves it so the user is outside all inventories. */
    private void setUpStockedShop() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);
        Processor.process(itemAdd("Rice", "Food", "10.5"), state);
        Processor.process(itemAdd("Pen", null, null), state);
        state.leaveInventory();
    }
}
