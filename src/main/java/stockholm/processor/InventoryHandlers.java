package stockholm.processor;

import static stockholm.processor.StateGuards.requireInventory;

import java.util.LinkedHashMap;
import java.util.Map;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.Order;
import stockholm.order.TransferOrder;
import stockholm.state.AppState;

/** Handles commands that manage inventories: {@code inv add|rename|delete|list}, {@code enter} and {@code back}. */
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

    /** Renames an inventory and all order endpoints that refer to it, including history. */
    static Result rename(Command command, AppState state) throws StockHolmException {
        String oldName = command.getArg(ArgKey.INV_NAME);
        String newName = command.getArg(ArgKey.INV_NEW_NAME);
        Inventory target = requireInventory(oldName, state);
        if (newName.isBlank()) {
            throw new StockHolmException("New inventory name cannot be blank.");
        }
        Inventory existing = state.findInventory(newName);
        if (existing != null && existing != target) {
            throw new StockHolmException("Inventory already exists: " + newName);
        }
        checkRenameKeepsTransfersValid(oldName, newName, state);
        target.rename(newName);
        state.getOrderHistory().renameInventoryReferences(oldName, newName);
        return new Result("Renamed inventory: " + oldName + " -> " + newName, false);
    }

    /** Rejects a rename that would make a saved transfer refer to the same inventory at both endpoints. */
    private static void checkRenameKeepsTransfersValid(String oldName, String newName, AppState state)
            throws StockHolmException {
        int orderNumber = 1;
        for (Order order : state.getOrderHistory().getOrders()) {
            if (order instanceof TransferOrder) {
                String source = oldName.equals(order.getSrc()) ? newName : order.getSrc();
                String destination = oldName.equals(order.getDst()) ? newName : order.getDst();
                if (source.equals(destination)) {
                    throw new StockHolmException("Cannot rename " + oldName + " to " + newName
                            + ": transfer order " + orderNumber + " would have the same source and destination.");
                }
            }
            orderNumber++;
        }
    }

    /**
     * Deletes an inventory and its items. Its orders stay in the order history, but open ones are canceled,
     * so an inventory created later with the same name cannot receive or refund their stock.
     */
    static Result delete(Command command, AppState state) throws StockHolmException {
        String name = command.getArg(ArgKey.INV_NAME);
        Inventory target = requireInventory(name, state);
        refundIncomingTransfers(target, state);
        state.getInventories().remove(target);
        int canceledCount = state.getOrderHistory().cancelOpenOrdersOf(name);
        String message = "Deleted inventory: " + name;
        if (canceledCount > 0) {
            message += "\nCanceled " + canceledCount + " open order(s) of " + name + ".";
        }
        return new Result(message, false);
    }

    /** Validates all incoming transfer refunds before applying any, so a rejected deletion changes nothing. */
    private static void refundIncomingTransfers(Inventory target, AppState state) throws StockHolmException {
        Map<Order, Inventory> refunds = new LinkedHashMap<>();
        Map<Inventory, Inventory> previews = new LinkedHashMap<>();
        for (Order order : state.getOrderHistory().getOrders()) {
            if (!(order instanceof TransferOrder) || !order.isOpen() || !target.getName().equals(order.getDst())) {
                continue;
            }
            Inventory source = state.findInventory(order.getSrc());
            // A missing source can occur after loading damaged inventory data. The order must still be closed
            // so recreating the deleted destination cannot take over this transfer.
            if (source == null || source == target) {
                continue;
            }
            Inventory preview = previews.get(source);
            if (preview == null) {
                preview = new Inventory(source.getName());
                for (Item item : source.getItems()) {
                    preview.addItem(copyForRefund(item));
                }
                previews.put(source, preview);
            }
            // Simulate cumulative refunds too: two orders may carry different types for the same item name.
            preview.addOrMerge(copyForRefund(order.getItem()));
            refunds.put(order, source);
        }
        for (Map.Entry<Order, Inventory> refund : refunds.entrySet()) {
            refund.getValue().addOrMerge(copyForRefund(refund.getKey().getItem()));
        }
    }

    /** Copies stock and its source minimum without sharing the order's recorded item. */
    private static Item copyForRefund(Item item) {
        Item copy = new Item(item.getName(), item.getType(), item.getCount());
        copy.setMinCount(item.getMinCount());
        return copy;
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
