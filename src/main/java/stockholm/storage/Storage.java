package stockholm.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
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
     * Restores inventories and their items, then merges saved orders into them by inventory name.
     * A corrupted inventory file, or either file with entries that had to be skipped, is first copied to a
     * timestamped {@code .bak} file next to it, so the next save cannot destroy data that was not loaded.
     *
     * @param state the application's data, normally empty at startup
     * @return any warnings, followed by a summary of what was loaded
     * @throws StockHolmException if a save file exists but cannot be read
     */
    public Result load(AppState state) throws StockHolmException {
        List<String> messages = new ArrayList<>();
        int inventoryCount = loadInventoriesInto(state, messages);
        int orderCount = loadOrdersInto(state, messages);
        messages.add("Loaded " + inventoryCount + " inventory(ies) and " + orderCount + " order(s).");
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
        List<String> failures = new ArrayList<>();
        Exception cause = null;
        if (changesInventories(command.type())) {
            try {
                inventoryStorage.save(state.getInventories());
            } catch (IOException | SecurityException e) {
                failures.add("inventories could not be saved: " + e.getMessage());
                cause = e;
            }
        }
        if (changesOrders(command.type())) {
            try {
                orderStorage.save(state.getInventories());
            } catch (IOException | SecurityException e) {
                failures.add("orders could not be saved: " + e.getMessage());
                cause = (cause == null) ? e : cause;
            }
        }
        if (!failures.isEmpty()) {
            throw new StockHolmException("Changes were applied in memory, but "
                    + String.join("; ", failures), cause);
        }
    }

    /** Commands after which inventories or items differ. New commands that change them must be added here. */
    private static boolean changesInventories(CommandType type) {
        switch (type) {
        case INV_ADD:
        case INV_DELETE:
        case ITEM_ADD:
        case ITEM_DELETE:
        case EXPORT:
        case DELIVER:
            return true;
        default:
            return false;
        }
    }

    /** Commands after which orders differ. New commands that change orders must be added here. */
    private static boolean changesOrders(CommandType type) {
        switch (type) {
        case IMPORT:
        case EXPORT:
        case APPROVE:
        case DELIVER:
        case INV_DELETE:
            return true;
        default:
            return false;
        }
    }

    /** Adds saved inventories to {@code state}, appending warnings to {@code messages}; returns how many loaded. */
    private int loadInventoriesInto(AppState state, List<String> messages) throws StockHolmException {
        Path file = inventoryStorage.getFilePath();
        InventoryStorage.LoadResult loaded;
        try {
            loaded = inventoryStorage.load();
        } catch (InventoryStorage.CorruptFileException e) {
            messages.add(file + " is corrupted (" + e.getMessage() + ").");
            messages.add(backUp(file) + " Starting with no inventories.");
            return 0;
        } catch (IOException | SecurityException e) {
            throw new StockHolmException("Cannot load inventories: " + e.getMessage(), e);
        }
        state.getInventories().addAll(loaded.inventories());
        if (!loaded.warnings().isEmpty()) {
            messages.addAll(loaded.warnings());
            messages.add(backUp(file));
        }
        return loaded.loadedCount();
    }

    /** Copies a save file to a timestamped backup and describes the outcome, without throwing. */
    private static String backUp(Path file) {
        try {
            return "Original file copied to " + BackupFile.create(file) + ".";
        } catch (IOException | SecurityException e) {
            return "Could not back up " + file + ": " + e.getMessage();
        }
    }

    /** Merges saved orders into {@code state}, appending warnings to {@code messages}; returns how many loaded. */
    private int loadOrdersInto(AppState state, List<String> messages) throws StockHolmException {
        OrderStorage.LoadResult loaded;
        try {
            loaded = orderStorage.load();
        } catch (IOException | SecurityException e) {
            throw new StockHolmException("Cannot load orders: " + e.getMessage(), e);
        }
        for (Inventory restored : loaded.inventories()) {
            Inventory existing = state.findInventory(restored.getName());
            if (existing == null) {
                state.getInventories().add(restored);
            } else {
                for (Order order : restored.getOrders()) {
                    existing.addOrder(order);
                }
            }
        }
        if (!loaded.warnings().isEmpty()) {
            messages.addAll(loaded.warnings());
            messages.add(backUp(orderStorage.getFilePath()));
        }
        return loaded.loadedCount();
    }
}
