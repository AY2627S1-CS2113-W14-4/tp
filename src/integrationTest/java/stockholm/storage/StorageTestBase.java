package stockholm.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.order.Order;
import stockholm.processor.Processor;
import stockholm.processor.Result;
import stockholm.state.AppState;

/**
 * Shared state and helpers for the {@link Storage} integration tests, using isolated files.
 */
abstract class StorageTestBase {
    protected AppState state;

    protected ArrayList<Inventory> inventories;

    @TempDir
    protected Path temporaryDirectory;

    @BeforeEach
    public void setUp() {
        state = new AppState();
        inventories = state.getInventories();
    }

    /** Returns an inventory storage in the temporary folder, so no test touches the real data folder. */
    protected InventoryStorage inventoryStorage() {
        return new InventoryStorage(temporaryDirectory.resolve("inventories.json"));
    }

    /** Mirrors the main loop: save only after command processing succeeds. */
    protected Result processAndSave(Command command, Storage storage) throws StockHolmException {
        Result result = Processor.process(command, state);
        storage.saveAfterCommand(command, state);
        return result;
    }

    /** Shortcut to every order in {@code state}'s order history. */
    protected List<Order> orders() {
        return state.getOrderHistory().getOrders();
    }

    /** Creates an inventory named "Shop" and enters it. */
    protected Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }
}
