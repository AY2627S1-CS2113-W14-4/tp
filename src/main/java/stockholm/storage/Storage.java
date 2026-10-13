package stockholm.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.order.Order;
import stockholm.processor.Result;
import stockholm.state.AppState;

/** Coordinates application persistence through the storage implementations it owns. */
public class Storage {
    private final OrderStorage orderStorage;

    /** Uses the project's shared order save file. */
    public Storage() {
        this(new OrderStorage());
    }

    /** Uses a supplied order storage, e.g. one with an isolated path in a test. */
    public Storage(OrderStorage orderStorage) {
        this.orderStorage = Objects.requireNonNull(orderStorage);
    }

    /** Restores orders at startup, leaving stock unchanged and reporting warnings through the normal Result flow. */
    public Result loadOrders(AppState state) throws StockHolmException {
        OrderStorage.LoadResult loaded;
        try {
            loaded = orderStorage.load();
        } catch (IOException | SecurityException e) {
            throw new StockHolmException("Cannot load orders: " + e.getMessage(), e);
        }
        for (Inventory restored : loaded.inventories()) {
            Inventory existing = findInventory(restored.getName(), state.getInventories());
            if (existing == null) {
                state.getInventories().add(restored);
            } else {
                for (Order order : restored.getOrders()) {
                    existing.addOrder(order);
                }
            }
        }
        List<String> messages = new ArrayList<>(loaded.warnings());
        messages.add("Loaded " + loaded.loadedCount() + " order(s) successfully.");
        return new Result(String.join("\n", messages), false);
    }

    /**
     * Saves all orders if a successfully processed command changed orders or deleted an inventory.
     * The caller must invoke this only after the Processor returns successfully.
     *
     * @param command the successfully processed command
     * @param state the application's data after processing
     * @throws StockHolmException if the changes cannot be saved
     */
    public void saveAfterCommand(Command command, AppState state) throws StockHolmException {
        // TODO: Add inventory saving after inventory related commands are executed here
        switch (command.type()) {
        case IMPORT:
        case EXPORT:
        case APPROVE:
        case DELIVER:
        case INV_DELETE:
            try {
                orderStorage.save(state.getInventories());
            } catch (IOException | SecurityException e) {
                throw new StockHolmException("Changes were applied in memory, but orders could not be saved: "
                        + e.getMessage(), e);
            }
            break;
        default:
            break;
        }
    }

    /** Returns the inventory with the given name, or {@code null} if there is none. */
    private Inventory findInventory(String name, List<Inventory> inventories) {
        for (Inventory inventory : inventories) {
            if (inventory.getName().equals(name)) {
                return inventory;
            }
        }
        return null;
    }
}
