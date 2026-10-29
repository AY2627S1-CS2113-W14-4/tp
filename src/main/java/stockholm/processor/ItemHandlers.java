package stockholm.processor;

import static stockholm.processor.ItemArgs.countOrDefault;
import static stockholm.processor.ItemArgs.typeOrEmpty;
import static stockholm.processor.StateGuards.requireItemAtIndex;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;
import stockholm.storage.ItemCsvReader;

/** Handles {@code item add|list|delete|edit|import|min} in the current inventory. */
final class ItemHandlers {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(ItemHandlers.class.getName());

    private ItemHandlers() {
    }

    /**
     * Adds a new item, or merges into an existing item with the same name by adding the counts.
     * Merging is refused if a different {@code --type} is given, so a type is never silently lost.
     */
    static Result add(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        // Without --type the type is empty, which Inventory never treats as a conflict.
        Item item = new Item(command.getArg(ArgKey.ITEM_NAME), typeOrEmpty(command), countOrDefault(command));
        Item stored = inventory.addOrMerge(item);
        if (stored != item) {
            return new Result("Added " + Item.formatCount(item.getCount()) + " to existing item: " + stored, false);
        }
        return new Result("Added item: " + item, false);
    }

    /** Lists the items of the current inventory with their one-based numbers. */
    static Result list(Command command, AppState state) {
        return new Result(ResultFormatter.itemList(state.getCurrentInventory()), false);
    }

    /** Deletes the item at the one-based position shown by {@code item list}. */
    static Result delete(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        // The Parser has already checked that the index is a positive whole number.
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        requireItemAtIndex(index, inventory);
        Item removed = inventory.removeItem(index - 1);
        return new Result("Deleted item: " + removed, false);
    }

    /** Imports a CSV file only after every row and merge has been validated. */
    static Result importCsv(Command command, AppState state) throws StockHolmException {
        String fileName = command.getArg(ArgKey.CSV_PATH);
        List<ItemCsvReader.Row> rows;
        try {
            rows = ItemCsvReader.read(Path.of(fileName));
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw new StockHolmException("Cannot read CSV file " + fileName + ": " + e.getMessage(), e);
        }
        Inventory inventory = state.getCurrentInventory();
        Inventory preview = new Inventory(inventory.getName());
        for (Item item : inventory.getItems()) {
            Item copy = new Item(item.getName(), item.getType(), item.getCount());
            copy.setMinCount(item.getMinCount());
            preview.addItem(copy);
        }
        applyCsvRows(rows, preview);
        applyCsvRows(rows, inventory);
        return new Result("Imported " + rows.size() + " item row(s) into " + inventory.getName() + ".", false);
    }

    /** Applies validated rows to a preview or the real inventory using the item-add merge rule. */
    private static void applyCsvRows(List<ItemCsvReader.Row> rows, Inventory inventory) throws StockHolmException {
        for (ItemCsvReader.Row row : rows) {
            Item incoming = new Item(row.name(), row.type(), row.count());
            Item stored;
            try {
                stored = inventory.addOrMerge(incoming);
            } catch (StockHolmException e) {
                throw new StockHolmException("CSV row " + row.number() + ": " + e.getMessage(), e);
            }
            if (!Double.isFinite(stored.getCount())) {
                throw new StockHolmException("CSV row " + row.number() + ": total count is too large.");
            }
            if (row.minimum() != null) {
                stored.setMinCount(row.minimum());
            }
        }
    }

    /** Edits an item's name or type without changing its count, minimum, or list position. */
    static Result edit(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        Item current = requireItemAtIndex(index, inventory);
        String name = command.hasArg(ArgKey.ITEM_NAME) ? command.getArg(ArgKey.ITEM_NAME) : current.getName();
        String type = command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : current.getType();
        Item duplicate = inventory.findItem(name);
        if (duplicate != null && duplicate != current) {
            throw new StockHolmException("Item already exists: " + name);
        }
        Item edited = current.withDetails(name, type);
        inventory.replaceItem(index - 1, edited);
        return new Result("Updated item: " + edited, false);
    }

    /**
     * Sets or clears the minimum stock level of the item at a one-based position in {@code item list}.
     * If the item is already below its new minimum, the result includes a low-stock alert straight away.
     */
    static Result setMinimum(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        double min = Double.parseDouble(command.getArg(ArgKey.ITEM_MIN));
        assert index > 0 && Double.isFinite(min) && min >= 0 : "The Parser checks the item number and minimum";
        Item item = requireItemAtIndex(index, inventory);
        item.setMinCount(min);
        LOGGER.log(Level.FINE, "Set minimum of {0} in {1} to {2}",
                new Object[]{item.getName(), inventory.getName(), min});
        if (min == 0) {
            return new Result("Cleared the minimum stock of " + item.getName() + ".", false);
        }
        String message = "Set the minimum stock of " + item.getName() + " to " + Item.formatCount(min) + ".";
        if (item.isLowOnStock()) {
            message += "\n" + ResultFormatter.lowStockAlert(item);
        }
        return new Result(message, false);
    }
}
