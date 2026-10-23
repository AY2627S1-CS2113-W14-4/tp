package stockholm.processor;

import static stockholm.processor.ItemHandlers.countOrDefault;
import static stockholm.processor.ItemHandlers.requireItem;
import static stockholm.processor.ItemHandlers.typeOrEmpty;

import java.util.List;

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
 * {@code approve} and {@code deliver}.
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
        return formatOrderCreated(order);
    }

    /** Creates an export order and immediately reduces stock, removing items whose count reaches zero. */
    static Result createExport(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        assert inventory != null : "Export requires a current inventory.";
        int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
        assert index > 0 : "The parser must supply a positive export item number.";
        Item existing = requireItem(index, inventory);
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
        return formatOrderCreated(order);
    }

    /** Lists every order in creation order without changing orders or stock. */
    static Result list(Command command, AppState state) {
        Inventory inventory = state.getCurrentInventory();
        List<Order> orders = inventory.getOrders();
        if (orders.isEmpty()) {
            return new Result("No orders in " + inventory.getName() + " yet.", false);
        }
        StringBuilder message = new StringBuilder("Orders in " + inventory.getName() + ":");
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            Item item = order.getItem();
            message.append("\n").append(i + 1).append(". ").append(order.getKindLabel())
                    .append("\n   State: ").append(order.getState())
                    .append("\n   Item name: ").append(item.getName())
                    .append("\n   Item type: ").append(item.getType())
                    .append("\n   Item count: ").append(Item.formatCount(item.getCount()));
        }
        return new Result(message.toString(), false);
    }

    /** Approves the order at the one-based position shown by {@code order list}. Stock is unchanged. */
    static Result approve(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        requireOrder(index, inventory).approve();
        return new Result("Approved order " + index + " in " + inventory.getName() + ".", false);
    }

    /**
     * Marks the order at the one-based position shown by {@code order list} as delivered.
     * An import order's item is then added to stock; an export order's stock was already reduced on creation.
     */
    static Result deliver(Command command, AppState state) throws StockHolmException {
        Inventory inventory = state.getCurrentInventory();
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        Order order = requireOrder(index, inventory);
        Item orderItem = order.getItem();
        boolean isImport = order instanceof ImportOrder;
        // Checked before deliver() changes the state, so a rejected delivery changes nothing.
        if (isImport && !inventory.checkCanMerge(orderItem)) {
            throw new StockHolmException("Item already exists with a different type: "
                    + inventory.findItem(orderItem.getName()) + ".");
        }
        order.deliver();
        if (isImport) {
            // A copy, so later stock changes do not alter the order's recorded item.
            inventory.addOrMerge(new Item(orderItem.getName(), orderItem.getType(), orderItem.getCount()));
        }
        return new Result("Delivered order " + index + " in " + inventory.getName() + ".", false);
    }

    /** Describes a newly created order and its item. */
    private static Result formatOrderCreated(Order order) {
        Item item = order.getItem();
        return new Result(order.getKindLabel() + " created:\nItem name: " + item.getName()
                + "\nItem type: " + item.getType()
                + "\nItem count: " + Item.formatCount(item.getCount()), false);
    }

    /**
     * Returns the order at a one-based position in {@code order list}, or throws if there is none.
     *
     * @param index one-based position, as shown to the user
     */
    private static Order requireOrder(int index, Inventory inventory) throws StockHolmException {
        List<Order> orders = inventory.getOrders();
        if (index > orders.size()) {
            throw new StockHolmException("No order number " + index + ". " + inventory.getName()
                    + " has " + orders.size() + " order(s); use order list to see them.");
        }
        return orders.get(index - 1);
    }
}
