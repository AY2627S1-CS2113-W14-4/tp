package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.parser.Parser;
import stockholm.state.AppState;

/**
 * Tests {@code item min}, {@code restock} and low-stock alerts through {@link Processor#process(Command, AppState)},
 * using commands built by {@link stockholm.parser.Parser} or directly.
 */
class LowStockTest extends ProcessorTestBase {
    @Test
    public void process_itemMinAboveCount_setsMinimumAndAlerts() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();

        Result result = Processor.process(Parser.parseCommand("item min 1 5"), state);

        assertEquals("Set the minimum stock of Pen to 5.\n"
                + "Low stock: Pen has 3 left, below its minimum of 5. Reorder at least 2.", result.message());
        assertEquals(5, shop.getItems().get(0).getMinCount());
    }

    @Test
    public void process_itemMinBelowCount_setsMinimumWithoutAlert() throws StockHolmException {
        enterShopWithPenAndRice();
        Result result = Processor.process(Parser.parseCommand("item min 2 4.5"), state);
        assertEquals("Set the minimum stock of Rice to 4.5.", result.message());
    }

    @Test
    public void process_itemMinZero_clearsMinimum() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);

        Result result = Processor.process(Parser.parseCommand("item min 1 0"), state);

        assertEquals("Cleared the minimum stock of Pen.", result.message());
        assertEquals(0, shop.getItems().get(0).getMinCount());
    }

    @Test
    public void process_itemMinIndexTooLarge_throwsException() {
        enterShopWithPenAndRice();
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("item min 3 5"), state));
        assertEquals("No item number 3. Shop has 2 item(s); use item list to see them.", e.getMessage());
    }

    @Test
    public void process_itemMinOutsideInventory_throwsException() {
        assertThrows(StockHolmException.class, () -> Processor.process(Parser.parseCommand("item min 1 5"), state));
    }

    @Test
    public void process_exportBelowMinimum_alertsLowStock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(8);

        Result result = Processor.process(Parser.parseCommand("export 2 --count=4"), state);

        assertTrue(result.message().endsWith(
                "\nLow stock: Rice has 6 left, below its minimum of 8. Reorder at least 2."), result.message());
    }

    @Test
    public void process_exportStillAboveMinimum_hasNoAlert() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(5);

        Result result = Processor.process(Parser.parseCommand("export 2 --count=4"), state);

        assertFalse(result.message().contains("Low stock"), result.message());
    }

    @Test
    public void process_exportAllWithMinimum_alertsOutOfStock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(2);

        Result result = Processor.process(Parser.parseCommand("export 1"), state);

        assertTrue(result.message().endsWith(
                "\nOut of stock: Pen has run out and was removed, along with its minimum of 2."), result.message());
        assertEquals(1, shop.getItems().size());
    }

    @Test
    public void process_stockWithLowItem_marksIt() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);

        Result result = Processor.process(stock(null, null), state);

        assertEquals("Stock levels in Shop:\n1. Pen (Stationery) x3 [LOW: min 5]\n2. Rice (Food) x10"
                + "\nTotal: 2 item(s), 13 unit(s)", result.message());
    }

    @Test
    public void process_restock_listsOnlyLowItemsWithReorderAmounts() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(0).setMinCount(5);
        shop.getItems().get(1).setMinCount(10);
        state.leaveInventory();

        Result result = Processor.process(Parser.parseCommand("restock Shop"), state);

        assertEquals("Items to restock in Shop:\n1. Pen (Stationery) x3 (min 5, reorder 2)", result.message());
    }

    @Test
    public void process_restockNoneLow_returnsNothingToRestock() throws StockHolmException {
        Inventory shop = enterShopWithPenAndRice();
        shop.getItems().get(1).setMinCount(5);
        assertEquals("Nothing to restock in Shop.", Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockNoMinimums_explainsHowToSetOne() throws StockHolmException {
        enterShopWithPenAndRice();
        assertEquals("No items in Shop have a minimum stock level. Set one with item min INDEX COUNT.",
                Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockOutsideWithoutName_listsEveryInventory() throws StockHolmException {
        setUpTwoInventoriesWithMinimums();

        Result result = Processor.process(Parser.parseCommand("restock"), state);

        assertEquals("Items to restock:\nShop:\n  1. Pen (Stationery) x3 (min 5, reorder 2)"
                + "\nWarehouse:\n  1. Box x1 (min 4, reorder 3)", result.message());
        assertFalse(state.isInsideInventory());
    }

    @Test
    public void process_restockOutsideWithoutName_skipsInventoriesWithNothingLow() throws StockHolmException {
        setUpTwoInventoriesWithMinimums();
        state.findInventory("Shop").getItems().get(0).setMinCount(0);

        Result result = Processor.process(Parser.parseCommand("restock"), state);

        assertEquals("Items to restock:\nWarehouse:\n  1. Box x1 (min 4, reorder 3)", result.message());
    }

    @Test
    public void process_restockOutsideNothingLowAnywhere_returnsNothingToRestock() throws StockHolmException {
        setUpTwoInventoriesWithMinimums();
        state.findInventory("Shop").getItems().get(0).setMinCount(3);
        state.findInventory("Warehouse").getItems().get(0).setMinCount(1);

        assertEquals("Nothing to restock in any inventory.",
                Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockOutsideNoMinimumsAnywhere_explainsHowToSetOne() throws StockHolmException {
        inventories.add(new Inventory("Empty"));
        assertEquals("No items in any inventory have a minimum stock level. Set one with item min INDEX COUNT.",
                Processor.process(Parser.parseCommand("restock"), state).message());
    }

    @Test
    public void process_restockInsideWithoutName_stillListsOnlyCurrentInventory() throws StockHolmException {
        setUpTwoInventoriesWithMinimums();
        state.enterInventory(state.findInventory("Warehouse"));

        Result result = Processor.process(Parser.parseCommand("restock"), state);

        assertEquals("Items to restock in Warehouse:\n1. Box x1 (min 4, reorder 3)", result.message());
    }

    /** Enters a new "Shop" holding Pen x3 and Rice x10, and returns it. */
    private Inventory enterShopWithPenAndRice() {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Pen", "Stationery", 3));
        shop.addItem(new Item("Rice", "Food", 10));
        return shop;
    }

    /** Creates "Shop" (Pen x3, min 5; Rice x10) and "Warehouse" (Box x1, min 4; Tape x2, min 1), outside both. */
    private void setUpTwoInventoriesWithMinimums() {
        Inventory shop = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 3);
        pen.setMinCount(5);
        shop.addItem(pen);
        shop.addItem(new Item("Rice", "Food", 10));
        Inventory warehouse = new Inventory("Warehouse");
        Item box = new Item("Box", "", 1);
        box.setMinCount(4);
        warehouse.addItem(box);
        Item tape = new Item("Tape", "", 2);
        tape.setMinCount(1);
        warehouse.addItem(tape);
        inventories.add(shop);
        inventories.add(warehouse);
    }
}
