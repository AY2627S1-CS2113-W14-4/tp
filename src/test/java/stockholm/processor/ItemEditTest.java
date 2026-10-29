package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.parser.Parser;
import stockholm.state.AppState;
import stockholm.storage.InventoryStorage;
import stockholm.storage.OrderStorage;
import stockholm.storage.Storage;

/** Tests editing item details through the same parser and processor as the app. */
class ItemEditTest {
    @TempDir
    Path tempDir;

    @Test
    public void itemEdit_nameAndType_preservesStockHistoryAndSavedData() throws Exception {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 3);
        pen.setMinCount(5);
        shop.addItem(pen);
        state.getOrderHistory().add(new ExportOrder(new Item("Pen", "Stationery", 1), shop));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addItem(new Item("Pen", "Stationery", 8));
        state.getInventories().add(shop);
        state.getInventories().add(warehouse);
        state.enterInventory(shop);

        Command command = Parser.parseCommand("item edit 1 --name=\"Blue Pen\" --type=Office");
        Result result = Processor.process(command, state);

        assertEquals("Updated item: Blue Pen (Office) x3", result.message());
        Item edited = shop.getItems().get(0);
        assertEquals("Blue Pen", edited.getName());
        assertEquals("Office", edited.getType());
        assertEquals(3, edited.getCount());
        assertEquals(5, edited.getMinCount());
        assertEquals("Pen", state.getOrderHistory().getOrders().get(0).getItem().getName());
        assertEquals("Pen", warehouse.getItems().get(0).getName());

        Path inventoryFile = tempDir.resolve("inventories.json");
        Storage storage = new Storage(new OrderStorage(tempDir.resolve("orders.csv")),
                new InventoryStorage(inventoryFile));
        storage.saveAfterCommand(command, state);
        Item loaded = new InventoryStorage(inventoryFile).load().inventories().get(0).getItems().get(0);
        assertEquals("Blue Pen", loaded.getName());
        assertEquals("Office", loaded.getType());
        assertEquals(3, loaded.getCount());
        assertEquals(5, loaded.getMinCount());
    }

    @Test
    public void itemEdit_duplicateName_rejectsWithoutChangingItems() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 3);
        Item rice = new Item("Rice", "Food", 2);
        shop.addItem(pen);
        shop.addItem(rice);
        state.getInventories().add(shop);
        state.enterInventory(shop);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(Parser.parseCommand("item edit 1 --name=Rice"), state));

        assertEquals("Item already exists: Rice", e.getMessage());
        assertSame(pen, shop.getItems().get(0));
        assertSame(rice, shop.getItems().get(1));
    }

    @Test
    public void itemEdit_emptyType_clearsCategory() throws StockHolmException {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Pen", "Stationery", 3));
        state.getInventories().add(shop);
        state.enterInventory(shop);

        Result result = Processor.process(Parser.parseCommand("item edit 1 --type=\"\""), state);

        assertEquals("Updated item: Pen x3", result.message());
        assertEquals("", shop.getItems().get(0).getType());
    }
}
