package stockholm.processor;

import static stockholm.processor.StateGuards.requireInventory;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.state.AppState;

/** Handles commands that manage inventories: {@code inv add|delete|list}, {@code enter} and {@code back}. */
final class InventoryHandlers {
    private InventoryHandlers() {
    }

    /** Creates an inventory; names must be unique. */
    static Result add(Command command, AppState state) throws StockHolmException {
        String name = command.getArg(ArgKey.INV_NAME);
        if (state.findInventory(name) != null) {
            throw new StockHolmException("Inventory already exists: " + name);
        }
        state.getInventories().add(new Inventory(name));
        return new Result("Added inventory: " + name, false);
    }

    /** Deletes an inventory, together with its items and orders. */
    static Result delete(Command command, AppState state) throws StockHolmException {
        String name = command.getArg(ArgKey.INV_NAME);
        Inventory target = requireInventory(name, state);
        state.getInventories().remove(target);
        return new Result("Deleted inventory: " + name, false);
    }

    /** Lists every inventory by name. */
    static Result list(Command command, AppState state) {
        return new Result(ResultFormatter.inventoryList(state.getInventories()), false);
    }

    /** Enters an inventory, switching directly if the user is already inside another one. */
    static Result enter(Command command, AppState state) throws StockHolmException {
        String name = command.getArg(ArgKey.INV_NAME);
        Inventory target = requireInventory(name, state);
        state.enterInventory(target);
        return new Result("Entered inventory: " + name, false);
    }

    /** Leaves the current inventory. */
    static Result back(Command command, AppState state) {
        Inventory current = state.getCurrentInventory();
        state.leaveInventory();
        return new Result("Left inventory: " + current.getName(), false);
    }
}
