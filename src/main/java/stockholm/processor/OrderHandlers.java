package stockholm.processor;

import static stockholm.processor.ItemArgs.countOrDefault;
import static stockholm.processor.ItemArgs.typeOrEmpty;
import static stockholm.processor.StateGuards.requireItemAtIndex;
import static stockholm.processor.StateGuards.requireOrderAtIndex;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.state.AppState;

/**
 * Handles orders in the current inventory: {@code import}, {@code export}, {@code order list},
 * {@code approve}, {@code reject}, {@code cancel} and {@code deliver}.
 */
final class OrderHandlers {
    private OrderHandlers() {
    }

    /** Creates a pending import order with its own item; current stock is unchanged. */
    static Result createImport(Command command, AppState state) {
        Inventory inventory = state.getCurrentInventory();
        Item item = new Item(command.getArg(ArgKey.ITEM_NAME), typeOrEmpty(command), countOrDefault(command));
        ImportOrder order = new ImportOrder(item, inventory);
        inventory.addOrder(order);
        return new Result(ResultFormatter.orderCreated(order), false);
    }

    /** Creates an export order and immediately reduces stock, removing items whose count reaches zero. */
    static Result createExport(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        assert inventory != null : "Export requires a current inventory.";
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        assert index > 0 : "The parser must supply a positive export item number.";
        Item existing = requireItemAtIndex(index, inventory);
        double available = existing.getCount();
        if (!Double.isFinite(available) || available <= 0) {
            throw new StockHolmException("Item has no valid stock to export: " + existing.getName());
        }
        double count = command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT)) : available;
        if (!Double.isFinite(count) || count <= 0) {
            throw new StockHolmException("Export count must be a positive finite number.");
        }
        if (count > available) {
            throw new StockHolmException("Cannot export " + Item.formatCount(count) + " of " + existing.getName()
                    + "; only " + Item.formatCount(available) + " available.");
        }

        Item exported = new Item(existing.getName(), existing.getType(), count);
        ExportOrder order = new ExportOrder(exported, inventory);
        assert order.getItem() != existing : "An export order must hold a separate item snapshot.";
        existing.reduceCount(count);
        assert exported.getCount() == count : "Export snapshot must not be affected by stock reduction.";
        assert Double.isFinite(existing.getCount()) && existing.getCount() >= 0
                : "Export must leave finite, non-negative stock.";
        if (existing.getCount() == 0) {
            inventory.removeItem(index - 1);
        }
        inventory.addOrder(order);
        return new Result(ResultFormatter.orderCreated(order), false);
    }

    /** Lists every order in creation order without changing orders or stock. */
    static Result list(Command command, AppState state) {
        return new Result(ResultFormatter.orderList(state.getCurrentInventory()), false);
    }

    /** Approves the order at the one-based position shown by {@code order list}. Stock is unchanged. */
    static Result approve(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        requireOrderAtIndex(index, inventory).approve();
        return new Result("Approved order " + index + " in " + inventory.getName() + ".", false);
    }

    /**
     * Rejects the order at the one-based position shown by {@code order list} with a reason.
     * An export order's deducted stock is refunded back into the current inventory.
     */
    static Result reject(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        String reason = command.getArg(ArgKey.ORDER_REASON);
        Order order = requireOrderAtIndex(index, inventory);
        changeState(order, inventory, order instanceof ExportOrder,
                () -> order.checkCanReject(reason), () -> order.reject(reason));
        return new Result("Rejected order " + index + " in " + inventory.getName() + ".", false);
    }

    /**
     * Cancels the order at the one-based position shown by {@code order list}.
     * An export order's deducted stock is refunded back into the current inventory.
     */
    static Result cancel(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        Order order = requireOrderAtIndex(index, inventory);
        changeState(order, inventory, order instanceof ExportOrder, order::checkCanCancel, order::cancel);
        return new Result("Canceled order " + index + " in " + inventory.getName() + ".", false);
    }

    /**
     * Marks the order at the one-based position shown by {@code order list} as delivered.
     * An import order's item is then added to stock; an export order's stock was already reduced on creation.
     */
    static Result deliver(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        Order order = requireOrderAtIndex(index, inventory);
        changeState(order, inventory, order instanceof ImportOrder, order::checkCanDeliver, order::deliver);
        return new Result("Delivered order " + index + " in " + inventory.getName() + ".", false);
    }

    /** A step of an order state change that may fail, e.g. {@link Order#checkCanCancel} or {@link Order#cancel}. */
    @FunctionalInterface
    private interface OrderStep {
        void run() throws StockHolmException;
    }

    /**
     * Changes an order's state and, if {@code addsStock}, adds a copy of its item to {@code inventory}.
     * The state change and the stock merge are both checked before anything changes, state first,
     * so a failed command changes nothing and reports the state problem ahead of a type conflict.
     *
     * @param addsStock whether this change puts the order's item into {@code inventory}
     * @param check     throws if the state change is not allowed, without changing anything
     * @param change    performs the state change
     */
    private static void changeState(Order order, Inventory inventory, boolean addsStock,
            OrderStep check, OrderStep change) throws StockHolmException {
        check.run();
        Item orderItem = order.getItem();
        if (addsStock && !inventory.checkCanMerge(orderItem)) {
            throw new StockHolmException("Item already exists with a different type: "
                    + inventory.findItem(orderItem.getName()) + ".");
        }
        change.run();
        if (addsStock) {
            // A copy, so later stock changes do not alter the order's recorded item.
            inventory.addOrMerge(new Item(orderItem.getName(), orderItem.getType(), orderItem.getCount()));
        }
    }
}
