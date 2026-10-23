package stockholm.processor;

import static stockholm.processor.StateGuards.requireNamedOrCurrentInventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/** Handles {@code stock [NAME] [--below=COUNT]}, which works anywhere. */
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
            return new Result("No items in " + inventory.getName() + " yet.", false);
        }
        if (command.hasArg(ArgKey.STOCK_BELOW)) {
            return showLowStock(inventory, command.getArg(ArgKey.STOCK_BELOW));
        }

        StringBuilder message = new StringBuilder("Stock levels in " + inventory.getName() + ":");
        // Summed as BigDecimal so e.g. 0.1 + 0.2 shows as 0.3, not 0.30000000000000004.
        BigDecimal totalUnits = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            assert item.getCount() > 0 && Double.isFinite(item.getCount())
                    : "Items in an inventory always have a positive count: " + item;
            message.append("\n").append(i + 1).append(". ").append(item);
            totalUnits = totalUnits.add(BigDecimal.valueOf(item.getCount()));
        }
        assert totalUnits.signum() > 0 : "A non-empty inventory has a positive total";
        message.append("\nTotal: ").append(items.size()).append(" item(s), ")
                .append(totalUnits.stripTrailingZeros().toPlainString()).append(" unit(s)");
        return new Result(message.toString(), false);
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
        StringBuilder message = new StringBuilder("Items in " + inventory.getName() + " below " + threshold + ":");
        int lowCount = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.getCount() < limit) {
                message.append("\n").append(i + 1).append(". ").append(item);
                lowCount++;
            }
        }
        assert lowCount <= items.size() : "Cannot list more low items than there are items";
        LOGGER.log(Level.FINE, "{0} of {1} item(s) in {2} are below {3}",
                new Object[]{lowCount, items.size(), inventory.getName(), threshold});
        if (lowCount == 0) {
            return new Result("No items in " + inventory.getName() + " below " + threshold + ".", false);
        }
        return new Result(message.toString(), false);
    }
}
