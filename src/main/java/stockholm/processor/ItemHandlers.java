package stockholm.processor;

import static stockholm.processor.ItemArgs.countOrDefault;
import static stockholm.processor.ItemArgs.typeOrEmpty;
import static stockholm.processor.StateGuards.requireItemAtIndex;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/** Handles {@code item add|list|delete} in the current inventory. */
final class ItemHandlers {
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
}
