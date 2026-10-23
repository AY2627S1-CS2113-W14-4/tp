package stockholm.processor;

import java.util.List;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/**
 * Handles {@code item add|list|delete} in the current inventory.
 * Also holds the item helpers that order commands reuse (default count and type, finding an item by number).
 */
final class ItemHandlers {
    /** Default count for item data when no {@code --count} is given. */
    private static final double DEFAULT_ITEM_COUNT = 1;

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
        Inventory inventory = state.getCurrentInventory();
        List<Item> items = inventory.getItems();
        if (items.isEmpty()) {
            return new Result("No items in " + inventory.getName() + " yet.", false);
        }
        StringBuilder message = new StringBuilder("Items in " + inventory.getName() + ":");
        for (int i = 0; i < items.size(); i++) {
            message.append("\n").append(i + 1).append(". ").append(items.get(i));
        }
        return new Result(message.toString(), false);
    }

    /** Deletes the item at the one-based position shown by {@code item list}. */
    static Result delete(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        // The Parser has already checked that the index is a positive whole number.
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        requireItem(index, inventory);
        Item removed = inventory.removeItem(index - 1);
        return new Result("Deleted item: " + removed, false);
    }

    /** Returns the {@code --count} argument as a number, or the default count if none was given. */
    static double countOrDefault(Command command) {
        return command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT))
                : DEFAULT_ITEM_COUNT;
    }

    /** Returns the {@code --type} argument, or an empty type if none was given. */
    static String typeOrEmpty(Command command) {
        return command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : "";
    }

    /**
     * Returns the item at a one-based position in {@code item list}, or throws if there is none.
     *
     * @param index one-based position, as shown to the user
     */
    static Item requireItem(int index, Inventory inventory) throws StockHolmException {
        List<Item> items = inventory.getItems();
        if (index > items.size()) {
            throw new StockHolmException("No item number " + index + ". " + inventory.getName()
                    + " has " + items.size() + " item(s); use item list to see them.");
        }
        return items.get(index - 1);
    }
}
