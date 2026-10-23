package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;

/**
 * Tests for {@link ResultFormatter}. Handlers and their rules are tested through {@code ProcessorTest};
 * these tests only check the layout of each message.
 */
class ResultFormatterTest {
    /** Returns an inventory named "Shop" holding the given items, in order. */
    private static Inventory shopWith(Item... items) {
        Inventory shop = new Inventory("Shop");
        for (Item item : items) {
            shop.addItem(item);
        }
        return shop;
    }

    @Test
    public void inventoryList_emptyAndNonEmpty_formatsNamesNumberedFromOne() {
        assertEquals("No inventories yet.", ResultFormatter.inventoryList(List.of()));
        assertEquals("Inventories:\n1. Shop\n2. Warehouse",
                ResultFormatter.inventoryList(List.of(new Inventory("Shop"), new Inventory("Warehouse"))));
    }

    @Test
    public void itemList_emptyAndNonEmpty_formatsItemsNumberedFromOne() {
        assertEquals("No items in Shop yet.", ResultFormatter.itemList(shopWith()));
        assertEquals("Items in Shop:\n1. Pen x3\n2. Rice (Food) x2.5",
                ResultFormatter.itemList(shopWith(new Item("Pen", "", 3), new Item("Rice", "Food", 2.5))));
    }

    @Test
    public void stockLevels_addsTotalsLineWithoutTrailingZeros() {
        Inventory shop = shopWith(new Item("Pen", "", 3), new Item("Rice", "Food", 2.5));
        assertEquals("Stock levels in Shop:\n1. Pen x3\n2. Rice (Food) x2.5\nTotal: 2 item(s), 5.5 unit(s)",
                ResultFormatter.stockLevels(shop, new BigDecimal("5.50")));
    }

    @Test
    public void lowStock_keepsItemListNumbersOrSaysNone() {
        Inventory shop = shopWith(new Item("Pen", "", 9), new Item("Rice", "Food", 1));
        Map<Integer, Item> lowItems = new LinkedHashMap<>();
        lowItems.put(2, shop.getItems().get(1));

        assertEquals("Items in Shop below 5:\n2. Rice (Food) x1", ResultFormatter.lowStock(shop, "5", lowItems));
        assertEquals("No items in Shop below 0.5.", ResultFormatter.lowStock(shop, "0.5", Map.of()));
    }

    @Test
    public void orderList_emptyAndNonEmpty_showsKindStateAndItem() throws StockHolmException {
        Inventory shop = shopWith();
        assertEquals("No orders in Shop yet.", ResultFormatter.orderList(shop));

        shop.addOrder(new ImportOrder(new Item("Pen", "", 3), shop));
        ExportOrder export = new ExportOrder(new Item("Rice", "Food", 2.5), shop);
        export.approve();
        shop.addOrder(export);
        assertEquals("Orders in Shop:"
                + "\n1. Import order\n   State: WAITING_APPROVAL\n   Item name: Pen\n   Item type: \n   Item count: 3"
                + "\n2. Export order\n   State: WAITING_FOR_DELIVERY\n   Item name: Rice\n   Item type: Food"
                + "\n   Item count: 2.5", ResultFormatter.orderList(shop));
    }

    @Test
    public void orderCreated_showsKindAndItemDetails() {
        Inventory shop = shopWith();
        assertEquals("Import order created:\nItem name: Pen\nItem type: \nItem count: 3",
                ResultFormatter.orderCreated(new ImportOrder(new Item("Pen", "", 3), shop)));
        assertEquals("Export order created:\nItem name: Rice\nItem type: Food\nItem count: 2.5",
                ResultFormatter.orderCreated(new ExportOrder(new Item("Rice", "Food", 2.5), shop)));
    }
}
