package stockholm.processor;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.Location;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.Order;
import stockholm.state.AppState;

/**
 * Checks where the user is and finds the inventory, item or order a command acts on.
 * The handlers use these so every command reports a wrong location or a missing inventory, item or order
 * the same way.
 */
final class StateGuards {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(StateGuards.class.getName());

    private StateGuards() {
    }

    /**
     * Throws if the user is not where a command may be used.
     * After this, commands for {@link Location#INSIDE} can rely on {@link AppState#getCurrentInventory()}.
     */
    static void checkLocation(Location location, AppState state) throws StockHolmException {
        switch (location) {
        case OUTSIDE:
            requireOutsideInventory(state);
            break;
        case INSIDE:
            requireInsideInventory(state);
            break;
        default:
            break;
        }
    }

    /** Throws if the user is inside an inventory, where {@code inv} commands are not allowed. */
    private static void requireOutsideInventory(AppState state) throws StockHolmException {
        if (state.isInsideInventory()) {
            throw new StockHolmException("You are inside " + state.getCurrentInventory().getName()
                    + ". Use back to leave it first.");
        }
    }

    /** Throws if the user is not inside an inventory, where {@code item} and order commands are needed. */
    private static void requireInsideInventory(AppState state) throws StockHolmException {
        if (!state.isInsideInventory()) {
            throw new StockHolmException("You are not inside an inventory. Use enter NAME first.");
        }
    }

    /**
     * Returns the inventory with the given name, or throws if there is none.
     *
     * @throws StockHolmException if no inventory has that name
     */
    static Inventory requireInventory(String name, AppState state) throws StockHolmException {
        assert name != null && !name.isEmpty() : "Parser should reject a missing inventory name";
        Inventory target = state.findInventory(name);
        if (target == null) {
            LOGGER.log(Level.FINE, "Rejected: no inventory named {0}", name);
            throw new StockHolmException("No such inventory: " + name);
        }
        return target;
    }

    /**
     * Returns the inventory named by the command's {@link ArgKey#INV_NAME} argument if it has one,
     * otherwise the current inventory. Used by commands that work both inside and outside an inventory
     * (e.g. {@code stock}), so a user can act on another inventory without entering it.
     * It never changes the current inventory.
     *
     * @param usage the command's syntax, shown if no name is given outside an inventory
     * @throws StockHolmException if the named inventory does not exist, or no name is given outside an inventory
     */
    static Inventory requireNamedOrCurrentInventory(Command command, AppState state, String usage)
            throws StockHolmException {
        assert usage != null && !usage.isBlank() : "Callers must give their command's usage";
        if (command.hasArg(ArgKey.INV_NAME)) {
            return requireInventory(command.getArg(ArgKey.INV_NAME), state);
        }
        if (!state.isInsideInventory()) {
            LOGGER.log(Level.FINE, "Rejected: no inventory name given outside an inventory ({0})", usage);
            throw new StockHolmException("Missing inventory name. Usage: " + usage + ", or enter an inventory first.");
        }
        Inventory current = state.getCurrentInventory();
        assert state.getInventories().contains(current) : "The current inventory must not have been deleted";
        return current;
    }

    /**
     * Returns the item at a one-based position in {@code item list}, or throws if there is none.
     *
     * @param index one-based position, as shown to the user
     * @throws StockHolmException if the inventory has fewer items than {@code index}
     */
    static Item requireItemAtIndex(int index, Inventory inventory) throws StockHolmException {
        List<Item> items = inventory.getItems();
        if (index > items.size()) {
            throw new StockHolmException("No item number " + index + ". " + inventory.getName()
                    + " has " + items.size() + " item(s); use item list to see them.");
        }
        return items.get(index - 1);
    }

    /**
     * Returns the order at a one-based position in {@code order list}, or throws if there is none.
     *
     * @param index one-based position, as shown to the user
     * @throws StockHolmException if the inventory has fewer orders than {@code index}
     */
    static Order requireOrderAtIndex(int index, Inventory inventory) throws StockHolmException {
        List<Order> orders = inventory.getOrders();
        if (index > orders.size()) {
            throw new StockHolmException("No order number " + index + ". " + inventory.getName()
                    + " has " + orders.size() + " order(s); use order list to see them.");
        }
        return orders.get(index - 1);
    }
}
