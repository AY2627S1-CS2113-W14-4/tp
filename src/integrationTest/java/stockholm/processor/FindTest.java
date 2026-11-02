package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/**
 * Tests the {@code find} command through {@link Processor#process(Command, AppState)}, using commands built by
 * {@link stockholm.parser.Parser} or directly.
 */
class FindTest extends ProcessorTestBase {
    @Test
    public void process_findOutsideInventory_searchesAllInventoriesIgnoringCase() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find("PEN", null), state);

        assertEquals("Found 3 item(s) matching \"PEN\":\nShop:\n  1. Blue Pen (Stationery) x3"
                + "\n  3. Pen Refill (stationery) x10\nWarehouse:\n  1. Pen x100", result.message());
    }

    @Test
    public void process_findInsideInventory_searchesOnlyCurrentInventory() throws StockHolmException {
        setUpTwoInventoriesWithPens();
        state.enterInventory(state.findInventory("Warehouse"));

        Result result = Processor.process(find("pen", null), state);

        assertEquals("Found 1 item(s) matching \"pen\":\nWarehouse:\n  1. Pen x100", result.message());
    }

    @Test
    public void process_findByType_matchesWholeTypeIgnoringCase() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find(null, "STATIONERY"), state);

        assertEquals("Found 2 item(s) of type STATIONERY:\nShop:\n  1. Blue Pen (Stationery) x3"
                + "\n  3. Pen Refill (stationery) x10", result.message());
        assertEquals("No items of type Station.",
                Processor.process(find(null, "Station"), state).message());
    }

    @Test
    public void process_findKeywordAndType_requiresBothToMatch() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Result result = Processor.process(find("refill", "Stationery"), state);

        assertEquals("Found 1 item(s) matching \"refill\" of type Stationery:\nShop:\n  3. Pen Refill (stationery) x10",
                result.message());
    }

    @Test
    public void process_findNoMatches_returnsNoItemsMessage() throws StockHolmException {
        setUpTwoInventoriesWithPens();
        assertEquals("No items matching \"chair\".", Processor.process(find("chair", null), state).message());
    }

    @Test
    public void process_findWithNoInventories_returnsNoItemsMessage() throws StockHolmException {
        assertEquals("No items matching \"pen\".", Processor.process(find("pen", null), state).message());
    }

    @Test
    public void process_find_doesNotChangeState() throws StockHolmException {
        setUpTwoInventoriesWithPens();

        Processor.process(find("pen", null), state);

        assertFalse(state.isInsideInventory());
        assertEquals(3, state.findInventory("Shop").getItems().size());
    }

    /** Builds a {@code find} command; pass {@code null} to leave out the keyword or the type. */
    private static Command find(String keyword, String type) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (keyword != null) {
            args.put(ArgKey.FIND_KEYWORD, keyword);
        }
        if (type != null) {
            args.put(ArgKey.ITEM_TYPE, type);
        }
        return new Command(CommandType.FIND, args);
    }

    /** Creates "Shop" (pens and rice) and "Warehouse" (a pen), leaving the user outside both. */
    private void setUpTwoInventoriesWithPens() {
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Blue Pen", "Stationery", 3));
        shop.addItem(new Item("Rice", "Food", 2.5));
        shop.addItem(new Item("Pen Refill", "stationery", 10));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "", 100));
        inventories.add(shop);
        inventories.add(warehouse);
    }
}
