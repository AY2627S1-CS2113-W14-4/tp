package stockholm.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import stockholm.command.Changes;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.order.Order;
import stockholm.processor.Result;
import stockholm.state.AppState;

/**
 * Coordinates application persistence through the storage implementations it owns:
 * {@link InventoryStorage} for inventories and items, and {@link OrderStorage} for orders.
 */
public class Storage {
    private final OrderStorage orderStorage;
    private final InventoryStorage inventoryStorage;

    /** Uses the project's shared save files. */
    public Storage() {
        this(new OrderStorage(), new InventoryStorage());
    }

    /** Uses supplied storages, e.g. ones with isolated paths in a test. */
    public Storage(OrderStorage orderStorage, InventoryStorage inventoryStorage) {
        this.orderStorage = Objects.requireNonNull(orderStorage);
        this.inventoryStorage = Objects.requireNonNull(inventoryStorage);
    }

    /**
     * Restores inventories and their items, then the order history. Orders are kept even if the inventory they
     * name no longer exists, and follow any inventory renamed on load. If any name was changed in either file, both
     * are then saved at once, so the files hold the new names and a later command that saves only one of them cannot
     * leave orders naming an inventory by its old name.
     * A corrupted inventory file, or either file with entries that had to be skipped, is first copied to a
     * timestamped {@code .bak} file next to it, so the next save cannot destroy data that was not loaded.
     *
     * @param state the application's data, normally empty at startup
     * @return any warnings, followed by a summary of what was loaded
     * @throws StockHolmException if a save file exists but cannot be read
     */
    public Result load(AppState state) throws StockHolmException {
        List<String> messages = new ArrayList<>();
        InventoryStorage.LoadResult inventories = loadInventoriesInto(state, messages);
        OrderStorage.LoadResult orders = loadOrdersInto(state, inventories.renamedInventories(), messages);
        if (inventories.namesChanged() || orders.namesChanged()) {
            try {
                save(state, true, true, "Inventories were renamed, but ");
                messages.add("Saved the new inventory names to both save files.");
            } catch (StockHolmException e) {
                messages.add(e.getMessage());
            }
        }
        messages.add("Loaded " + inventories.loadedCount() + " inventory(ies) and " + orders.orders().size()
                + " order(s).");
        return new Result(String.join("\n", messages), false);
    }

    /**
     * Saves whatever a successfully processed command changed: inventories and items, orders, or both.
     * Both saves are attempted even if one fails. The caller must invoke this only after the Processor
     * returns successfully.
     *
     * @param command the successfully processed command
     * @param state the application's data after processing
     * @throws StockHolmException if any of the changes cannot be saved
     */
    public void saveAfterCommand(Command command, AppState state) throws StockHolmException {
        Changes changes = command.type().changes();
        save(state, changes.includesInventories(), changes.includesOrders(), "Changes were applied in memory, but ");
    }

    /**
     * Saves the inventories, the orders, or both. Both saves are attempted even if one fails.
     *
     * @param failurePrefix starts the message of the exception thrown if a save fails
     * @throws StockHolmException if any of the files cannot be saved
     */
    private void save(AppState state, boolean savesInventories, boolean savesOrders, String failurePrefix)
            throws StockHolmException {
        List<String> failures = new ArrayList<>();
        Exception cause = null;
        if (savesInventories) {
            try {
                inventoryStorage.save(state.getInventories());
            } catch (IOException | SecurityException e) {
                failures.add("inventories could not be saved: " + e.getMessage());
                cause = e;
            }
        }
        if (savesOrders) {
            try {
                orderStorage.save(state.getOrderHistory());
            } catch (IOException | SecurityException e) {
                failures.add("orders could not be saved: " + e.getMessage());
                cause = (cause == null) ? e : cause;
            }
        }
        if (!failures.isEmpty()) {
            throw new StockHolmException(failurePrefix + String.join("; ", failures), cause);
        }
    }

    /** Adds saved inventories to {@code state}, appending warnings to {@code messages}; returns what was loaded. */
    private InventoryStorage.LoadResult loadInventoriesInto(AppState state, List<String> messages)
            throws StockHolmException {
        Path file = inventoryStorage.getFilePath();
        InventoryStorage.LoadResult loaded;
        try {
            loaded = inventoryStorage.load();
        } catch (InventoryStorage.CorruptFileException e) {
            messages.add(file + " is corrupted (" + e.getMessage() + ").");
            messages.add(backUp(file) + " Starting with no inventories.");
            return new InventoryStorage.LoadResult(List.of(), List.of(), 0, Map.of(), false);
        } catch (IOException | SecurityException e) {
            throw new StockHolmException("Cannot load inventories: " + e.getMessage(), e);
        }
        state.getInventories().addAll(loaded.inventories());
        if (!loaded.warnings().isEmpty()) {
            messages.addAll(loaded.warnings());
            messages.add(backUp(file));
        }
        return loaded;
    }

    /** Copies a save file to a timestamped backup and describes the outcome, without throwing. */
    private static String backUp(Path file) {
        try {
            return "Original file copied to " + BackupFile.create(file) + ".";
        } catch (IOException | SecurityException e) {
            return "Could not back up " + file + ": " + e.getMessage();
        }
    }

    /**
     * Adds saved orders to the order history, renaming their inventories as {@code renamedInventories} says,
     * and appending warnings to {@code messages}; returns what was loaded.
     */
    private OrderStorage.LoadResult loadOrdersInto(AppState state, Map<String, String> renamedInventories,
            List<String> messages) throws StockHolmException {
        OrderStorage.LoadResult loaded;
        try {
            loaded = orderStorage.load(renamedInventories);
        } catch (IOException | SecurityException e) {
            throw new StockHolmException("Cannot load orders: " + e.getMessage(), e);
        }
        for (Order order : loaded.orders()) {
            state.getOrderHistory().add(order);
        }
        if (!loaded.warnings().isEmpty()) {
            messages.addAll(loaded.warnings());
            messages.add(backUp(orderStorage.getFilePath()));
        }
        return loaded;
    }
}
