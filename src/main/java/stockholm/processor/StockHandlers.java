package stockholm.processor;

import static stockholm.processor.StateGuards.requireNamedOrCurrentInventory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/** Handles {@code stock [NAME] [--below=COUNT]} and {@code restock [NAME]}, which work anywhere. */
final class StockHandlers {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(StockHandlers.class.getName());

    private StockHandlers() {
    }

    /**
     * Shows every item of an inventory with its count, followed by the totals.
     * The inventory is the one named in the command, or the current one if no name is given,
     * so stock can be checked from anywhere without entering the inventory.
     * Items keep the numbers shown by {@code item list}.
     * With {@code --below=COUNT}, only items whose count is below {@code COUNT} are shown, to spot low stock.
     */
    static Result show(Command command, AppState state) throws StockHolmException {
        Inventory inventory = requireNamedOrCurrentInventory(command, state, CommandType.STOCK.usage());
        assert inventory != null : "requireNamedOrCurrentInventory never returns null";
        List<Item> items = inventory.getItems();
        String threshold = command.hasArg(ArgKey.STOCK_BELOW) ? command.getArg(ArgKey.STOCK_BELOW) : "none";
        LOGGER.log(Level.FINE, "Showing stock of {0} ({1} item(s)), threshold: {2}",
                new Object[]{inventory.getName(), items.size(), threshold});
        if (items.isEmpty()) {
            return new Result(ResultFormatter.noItems(inventory), false);
        }
        if (command.hasArg(ArgKey.STOCK_BELOW)) {
            return showLowStock(inventory, command.getArg(ArgKey.STOCK_BELOW));
        }

        // Summed as BigDecimal so e.g. 0.1 + 0.2 shows as 0.3, not 0.30000000000000004.
        BigDecimal totalUnits = BigDecimal.ZERO;
        for (Item item : items) {
            assert item.getCount() > 0 && Double.isFinite(item.getCount())
                    : "Items in an inventory always have a positive count: " + item;
            totalUnits = totalUnits.add(BigDecimal.valueOf(item.getCount()));
        }
        assert totalUnits.signum() > 0 : "A non-empty inventory has a positive total";
        return new Result(ResultFormatter.stockLevels(inventory, totalUnits), false);
    }

    /**
     * Shows only the items whose count is below a threshold, keeping their {@code item list} numbers.
     *
     * @param threshold a positive decimal, already checked by the Parser
     */
    private static Result showLowStock(Inventory inventory, String threshold) {
        double limit = Double.parseDouble(threshold);
        assert limit > 0 && Double.isFinite(limit) : "Parser only accepts positive, finite thresholds: " + threshold;
        List<Item> items = inventory.getItems();
        // Keyed by one-based number, in list order, so low items keep their item list numbers.
        Map<Integer, Item> lowItems = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getCount() < limit) {
                lowItems.put(i + 1, items.get(i));
            }
        }
        assert lowItems.size() <= items.size() : "Cannot list more low items than there are items";
        LOGGER.log(Level.FINE, "{0} of {1} item(s) in {2} are below {3}",
                new Object[]{lowItems.size(), items.size(), inventory.getName(), threshold});
        return new Result(ResultFormatter.lowStock(inventory, threshold, lowItems), false);
    }

    /**
     * Lists the items below their minimum, with how many to reorder. With a name, or inside an inventory, only that
     * inventory is checked; outside an inventory without a name, every inventory is, so one command shows
     * everything to reorder. Items keep their {@code item list} numbers.
     */
    static Result restock(Command command, AppState state) throws StockHolmException {
        if (!command.hasArg(ArgKey.INV_NAME) && !state.isInsideInventory()) {
            return restockAll(state.getInventories());
        }
        Inventory inventory = requireNamedOrCurrentInventory(command, state, CommandType.RESTOCK.usage());
        assert inventory != null : "requireNamedOrCurrentInventory never returns null";
        Map<Integer, Item> lowItems = findLowItems(inventory);
        LOGGER.log(Level.FINE, "{0} item(s) to restock in {1}", new Object[]{lowItems.size(), inventory.getName()});
        if (!hasAnyMinimum(inventory)) {
            return new Result("No items in " + inventory.getName()
                    + " have a minimum stock level. Set one with " + CommandType.ITEM_MIN.usage() + ".", false);
        }
        return new Result(ResultFormatter.restockList(inventory, lowItems), false);
    }

    /** Lists the items below their minimum in every inventory, grouped by inventory in creation order. */
    private static Result restockAll(List<Inventory> inventories) {
        Map<Inventory, Map<Integer, Item>> lowItemsByInventory = new LinkedHashMap<>();
        boolean hasAnyMinimum = false;
        int lowCount = 0;
        for (Inventory inventory : inventories) {
            hasAnyMinimum |= hasAnyMinimum(inventory);
            Map<Integer, Item> lowItems = findLowItems(inventory);
            if (!lowItems.isEmpty()) {
                lowItemsByInventory.put(inventory, lowItems);
                lowCount += lowItems.size();
            }
        }
        assert lowCount == 0 || hasAnyMinimum : "An item can only be low if it has a minimum";
        LOGGER.log(Level.FINE, "{0} item(s) to restock across {1} inventory(ies)",
                new Object[]{lowCount, inventories.size()});
        if (!hasAnyMinimum) {
            return new Result("No items in any inventory have a minimum stock level. Set one with "
                    + CommandType.ITEM_MIN.usage() + ".", false);
        }
        return new Result(ResultFormatter.restockListAll(lowItemsByInventory), false);
    }

    /** Returns the items of an inventory that are below their minimum, keyed by their one-based number. */
    private static Map<Integer, Item> findLowItems(Inventory inventory) {
        List<Item> items = inventory.getItems();
        Map<Integer, Item> lowItems = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isLowOnStock()) {
                lowItems.put(i + 1, items.get(i));
            }
        }
        return lowItems;
    }

    /** Returns whether any item of an inventory has a minimum stock level set. */
    private static boolean hasAnyMinimum(Inventory inventory) {
        for (Item item : inventory.getItems()) {
            if (item.getMinCount() > 0) {
                return true;
            }
        }
        return false;
    }
}
