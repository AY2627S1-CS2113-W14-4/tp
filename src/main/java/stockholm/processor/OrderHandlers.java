package stockholm.processor;

import static stockholm.processor.ItemArgs.countOrDefault;
import static stockholm.processor.ItemArgs.typeOrEmpty;
import static stockholm.processor.StateGuards.requireInventory;
import static stockholm.processor.StateGuards.requireItemAtIndex;
import static stockholm.processor.StateGuards.requireOrderAtIndex;

import java.util.ArrayList;
import java.util.List;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.TransferOrder;
import stockholm.state.AppState;

/**
 * Handles order creation with {@code import}, {@code export} and {@code transfer}, and history commands:
 * {@code order list}, {@code approve}, {@code reject}, {@code cancel} and {@code deliver}.
 */
final class OrderHandlers {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private OrderHandlers() {
    }

    /** Creates a pending import order with its own item; current stock is unchanged. */
    static Result createImport(Command command, AppState state) {
        Inventory inventory = state.getCurrentInventory();
        Item item = new Item(command.getArg(ArgKey.ITEM_NAME), typeOrEmpty(command), countOrDefault(command));
        ImportOrder order = new ImportOrder(item, inventory);
        state.getOrderHistory().add(order);
        return new Result(ResultFormatter.orderCreated(order), false);
    }

    /** Creates an export order and immediately reduces stock, removing items whose count reaches zero. */
    static Result createExport(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        assert inventory != null : "Export requires a current inventory.";
        return reserveStock(command, state, inventory, null);
    }

    /** Creates a transfer from existing stock; omitted endpoints refer to the current inventory. */
    static Result createTransfer(Command command, AppState state) throws StockHolmException {
        Inventory source = transferInventory(command, state, ArgKey.INV_FROM, "from");
        Inventory destination = transferInventory(command, state, ArgKey.INV_TO, "to");
        if (source == destination) {
            throw new StockHolmException("Source and destination inventories must be different.");
        }
        return reserveStock(command, state, source, destination);
    }

    /** Resolves one transfer endpoint without changing the user's current inventory. */
    private static Inventory transferInventory(Command command, AppState state, ArgKey key, String option)
            throws StockHolmException {
        if (command.hasArg(key)) {
            return requireInventory(command.getArg(key), state);
        }
        if (state.isInsideInventory()) {
            return state.getCurrentInventory();
        }
        throw new StockHolmException("Missing --" + option + " inventory name. Usage: " + command.type().usage()
                + ", or enter an inventory first.");
    }

    /** Shares stock validation, snapshots and low-stock alerts for exports and transfers. */
    private static Result reserveStock(Command command, AppState state, Inventory inventory, Inventory destination)
            throws StockHolmException {
        String action = destination == null ? "export" : "transfer";
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        assert index > 0 : "The parser must supply a positive item number.";
        Item existing = requireItemAtIndex(index, inventory);
        double available = existing.getCount();
        if (!Double.isFinite(available) || available <= 0) {
            throw new StockHolmException("Item has no valid stock to " + action + ": " + existing.getName());
        }
        double count = command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT)) : available;
        if (!Double.isFinite(count) || count <= 0) {
            throw new StockHolmException((destination == null ? "Export" : "Transfer")
                    + " count must be a positive finite number.");
        }
        if (count > available) {
            throw new StockHolmException("Cannot " + action + " " + Item.formatCount(count) + " of "
                    + existing.getName() + "; only " + Item.formatCount(available) + " available.");
        }

        Item reserved = new Item(existing.getName(), existing.getType(), count);
        // Minimums are per inventory: this is the source's, kept only so a refund back into the source can restore it
        // if the item runs out and is removed below.
        reserved.setMinCount(existing.getMinCount());
        if (destination != null && !destination.checkCanMerge(reserved)) {
            throw new StockHolmException("Item already exists with a different type: "
                    + destination.findItem(reserved.getName()) + ".");
        }
        Order order = destination == null ? new ExportOrder(reserved, inventory)
                : new TransferOrder(reserved, inventory, destination);
        assert order.getItem() != existing : "An order must hold a separate item snapshot.";
        existing.reduceCount(count);
        assert reserved.getCount() == count : "Order snapshot must not be affected by stock reduction.";
        assert Double.isFinite(existing.getCount()) && existing.getCount() >= 0
                : "Stock reservation must leave finite, non-negative stock.";
        String alert = "";
        if (existing.getCount() == 0) {
            inventory.removeItem(index - 1);
            if (existing.getMinCount() > 0) {
                alert = "\nOut of stock: " + existing.getName() + " has run out and was removed, along with its "
                        + "minimum of " + Item.formatCount(existing.getMinCount()) + ".";
            }
        } else if (existing.isLowOnStock()) {
            alert = "\n" + ResultFormatter.lowStockAlert(existing);
        }
        state.getOrderHistory().add(order);
        return new Result(ResultFormatter.orderCreated(order) + alert, false);
    }

    /**
     * Lists orders in creation order, filtered and paginated according to the command's arguments.
     *
     * @param command the parsed {@code order list} command
     * @param state the application state
     * @return the result containing the formatted order list
     * @throws StockHolmException if the page number is out of range
     */
    static Result list(Command command, AppState state) throws StockHolmException {
        int page = command.hasArg(ArgKey.ORDER_PAGE)
                ? Integer.parseInt(command.getArg(ArgKey.ORDER_PAGE))
                : DEFAULT_PAGE;
        int pageSize = command.hasArg(ArgKey.ORDER_PAGE_SIZE)
                ? Integer.parseInt(command.getArg(ArgKey.ORDER_PAGE_SIZE))
                : DEFAULT_PAGE_SIZE;
        String statusFilter = command.hasArg(ArgKey.ORDER_STATUS)
                ? command.getArg(ArgKey.ORDER_STATUS)
                : null;
        String invFilter = command.hasArg(ArgKey.ORDER_INV)
                ? command.getArg(ArgKey.ORDER_INV)
                : null;
        String typeFilter = command.hasArg(ArgKey.ORDER_TYPE)
                ? command.getArg(ArgKey.ORDER_TYPE)
                : null;

        List<Order> orders = state.getOrderHistory().getOrders();
        if (orders.isEmpty()) {
            if (page == 1) {
                return new Result("No orders yet.", false);
            }
            throw new StockHolmException("No page number " + page + ". There are no orders yet.");
        }

        List<IndexedOrder> matching = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (matches(order, statusFilter, invFilter, typeFilter)) {
                matching.add(new IndexedOrder(i + 1, order));
            }
        }

        if (matching.isEmpty()) {
            if (page == 1) {
                return new Result("No matching orders found.", false);
            }
            throw new StockHolmException("No page number " + page + ". There are no matching orders.");
        }

        int totalPages = (int) (((long) matching.size() + pageSize - 1) / pageSize);
        if (page > totalPages) {
            throw new StockHolmException("No page number " + page + ". There are " + totalPages
                    + " page(s); use order list to see the first page.");
        }

        return new Result(ResultFormatter.orderListIndexed(matching, page, totalPages, pageSize), false);
    }

    /** Returns whether an order matches the optional status, inventory, and order type filters. */
    private static boolean matches(Order order, String statusFilter, String invFilter, String typeFilter) {
        if (statusFilter != null && !order.getState().name().equalsIgnoreCase(statusFilter)) {
            return false;
        }
        if (invFilter != null) {
            boolean matchesSrc = order.getSrc() != null && order.getSrc().equals(invFilter);
            boolean matchesDst = order.getDst() != null && order.getDst().equals(invFilter);
            if (!matchesSrc && !matchesDst) {
                return false;
            }
        }
        if (typeFilter != null) {
            switch (typeFilter) {
            case "import":
                if (!(order instanceof ImportOrder)) {
                    return false;
                }
                break;
            case "export":
                if (!(order instanceof ExportOrder)) {
                    return false;
                }
                break;
            case "transfer":
                if (!(order instanceof TransferOrder)) {
                    return false;
                }
                break;
            default:
                return false;
            }
        }
        return true;
    }

    /** Approves the order at the one-based position shown by {@code order list}. Stock is unchanged. */
    static Result approve(Command command, AppState state) throws StockHolmException {
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        requireOrderAtIndex(index, state.getOrderHistory()).approve();
        return new Result("Approved order " + index + ".", false);
    }

    /**
     * Rejects the order at the one-based position shown by {@code order list} with a reason.
     * An export or transfer order's deducted stock is refunded back into its source inventory.
     */
    static Result reject(Command command, AppState state) throws StockHolmException {
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        String reason = command.getArg(ArgKey.ORDER_REASON);
        Order order = requireOrderAtIndex(index, state.getOrderHistory());
        changeState(order, state, refundsSource(order) ? order.getSrc() : null,
                () -> order.checkCanReject(reason), () -> order.reject(reason));
        return new Result("Rejected order " + index + ".", false);
    }

    /**
     * Cancels the order at the one-based position shown by {@code order list}.
     * An export or transfer order's deducted stock is refunded back into its source inventory.
     */
    static Result cancel(Command command, AppState state) throws StockHolmException {
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        Order order = requireOrderAtIndex(index, state.getOrderHistory());
        changeState(order, state, refundsSource(order) ? order.getSrc() : null, order::checkCanCancel, order::cancel);
        return new Result("Canceled order " + index + ".", false);
    }

    /**
     * Marks the order at the one-based position shown by {@code order list} as delivered.
     * An import or transfer order's item is added to its destination inventory; source stock was already
     * reserved when an export or transfer was created.
     */
    static Result deliver(Command command, AppState state) throws StockHolmException {
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        Order order = requireOrderAtIndex(index, state.getOrderHistory());
        boolean addsStock = order instanceof ImportOrder || order instanceof TransferOrder;
        changeState(order, state, addsStock ? order.getDst() : null, order::checkCanDeliver, order::deliver);
        return new Result("Delivered order " + index + ".", false);
    }

    /** Exports and transfers both reserve source stock when they are created. */
    private static boolean refundsSource(Order order) {
        return order instanceof ExportOrder || order instanceof TransferOrder;
    }

    /** A step of an order state change that may fail, e.g. {@link Order#checkCanCancel} or {@link Order#cancel}. */
    @FunctionalInterface
    private interface OrderStep {
        void run() throws StockHolmException;
    }

    /**
     * Changes an order's state and, if an inventory name is supplied, adds a copy of its item there.
     * Everything is checked before anything changes, state first, so a failed command changes nothing and
     * reports a state problem ahead of a missing inventory or a type conflict.
     *
     * @param inventoryName the inventory receiving stock, or null if stock is unchanged
     * @param check     throws if the state change is not allowed, without changing anything
     * @param change    performs the state change
     * @throws StockHolmException if the state change is not allowed, or stock must be added but the order's
     *                            inventory is missing or holds an item of the same name with a different type
     */
    private static void changeState(Order order, AppState state, String inventoryName,
            OrderStep check, OrderStep change) throws StockHolmException {
        check.run();
        if (inventoryName == null) {
            change.run();
            return;
        }
        Inventory inventory = state.findInventory(inventoryName);
        // Deleting an inventory cancels its open orders, so this only happens for orders loaded without their
        // inventory, e.g. after a corrupted inventory file.
        if (inventory == null) {
            throw new StockHolmException("Inventory " + inventoryName + " no longer exists.");
        }
        Item orderItem = order.getItem();
        if (!inventory.checkCanMerge(orderItem)) {
            throw new StockHolmException("Item already exists with a different type: "
                    + inventory.findItem(orderItem.getName()) + ".");
        }
        change.run();
        // A copy, so later stock changes do not alter the order's recorded item.
        Item copy = new Item(orderItem.getName(), orderItem.getType(), orderItem.getCount());
        // The recorded minimum belongs to the source inventory, so it is restored only on a refund back into the
        // source, never carried into another inventory. It only matters if the item is added anew: merging into an
        // existing item keeps that item's own minimum.
        if (inventory.getName().equals(order.getSrc())) {
            copy.setMinCount(orderItem.getMinCount());
        }
        inventory.addOrMerge(copy);
    }
}
