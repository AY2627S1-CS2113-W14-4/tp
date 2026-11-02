package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * Tests the {@code item add}, {@code item list} and {@code item delete} commands through {@link
 * Processor#process(Command, AppState)}, using commands built by {@link stockholm.parser.Parser} or directly.
 */
class ItemCommandsTest extends ProcessorTestBase {
    @Test
    public void process_itemCommandsOutsideInventory_throwException() {
        Command[] itemCommands = {itemAdd("Pen", null, null), new Command(CommandType.ITEM_LIST), itemDelete(1)};
        for (Command command : itemCommands) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Processor.process(command, state));
            assertEquals("You are not inside an inventory. Use enter NAME first.", e.getMessage());
        }
    }

    @Test
    public void process_itemAddWithAllOptions_addsItem() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);

        assertEquals("Added item: A4 Paper Case (Stationery) x2", result.message());
        Item item = shop.getItems().get(0);
        assertEquals("A4 Paper Case", item.getName());
        assertEquals("Stationery", item.getType());
        assertEquals(2.0, item.getCount());
    }

    @Test
    public void process_itemAddWithoutOptions_usesDefaults() throws StockHolmException {
        Inventory shop = enterNewShop();

        Result result = Processor.process(itemAdd("Pen", null, null), state);

        assertEquals("Added item: Pen x1", result.message());
        assertEquals("", shop.getItems().get(0).getType());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddExistingName_mergesCounts() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "2.5"), state);

        Result result = Processor.process(itemAdd("Rice", null, "1.5"), state);

        assertEquals("Added 1.5 to existing item: Rice (Food) x4", result.message());
        assertEquals(1, shop.getItems().size());
        assertEquals(4.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddDecimalMerge_showsExactCount() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("Rice", null, "0.1"), state);

        Result result = Processor.process(itemAdd("Rice", null, "0.2"), state);

        assertEquals("Added 0.2 to existing item: Rice x0.3", result.message());
    }

    @Test
    public void process_itemAddExistingNameSameType_mergesCounts() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "1"), state);

        Processor.process(itemAdd("Rice", "Food", "1"), state);

        assertEquals(2.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemAddExistingNameDifferentType_throwsAndKeepsCount() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("Rice", "Food", "1"), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(itemAdd("Rice", "Grain", "1"), state));

        assertEquals("Item already exists with a different type: Rice (Food) x1.", e.getMessage());
        assertEquals(1.0, shop.getItems().get(0).getCount());
    }

    @Test
    public void process_itemListEmpty_returnsNoItemsMessage() throws StockHolmException {
        enterNewShop();
        Result result = Processor.process(new Command(CommandType.ITEM_LIST), state);
        assertEquals("No items in Shop yet.", result.message());
    }

    @Test
    public void process_itemListNonEmpty_returnsNumberedList() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A4 Paper Case", "Stationery", "2"), state);
        Processor.process(itemAdd("Pen", null, null), state);

        Result result = Processor.process(new Command(CommandType.ITEM_LIST), state);

        assertEquals("Items in Shop:\n1. A4 Paper Case (Stationery) x2\n2. Pen x1", result.message());
    }

    @Test
    public void process_itemDeleteValidIndex_removesThatItem() throws StockHolmException {
        Inventory shop = enterNewShop();
        Processor.process(itemAdd("A", null, null), state);
        Processor.process(itemAdd("B", null, null), state);
        Processor.process(itemAdd("C", null, null), state);

        Result result = Processor.process(itemDelete(2), state);

        assertEquals("Deleted item: B x1", result.message());
        assertEquals(2, shop.getItems().size());
        assertEquals("A", shop.getItems().get(0).getName());
        assertEquals("C", shop.getItems().get(1).getName());
    }

    @Test
    public void process_itemDeleteIndexTooLarge_throwsException() throws StockHolmException {
        enterNewShop();
        Processor.process(itemAdd("A", null, null), state);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(itemDelete(2), state));
        assertEquals("No item number 2. Shop has 1 item(s); use item list to see them.", e.getMessage());
    }

    private static Command itemDelete(int index) {
        return new Command(CommandType.ITEM_DELETE, Map.of(ArgKey.ITEM_INDEX, String.valueOf(index)));
    }
}
