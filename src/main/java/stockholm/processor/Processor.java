package stockholm.processor;

import java.math.BigDecimal;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.exceptions.StockHolmException;
import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.state.AppState;

/**
 * Runs a parsed {@link Command} against the application state and reports what happened
 * as a {@link Result}. It enforces business rules (e.g. no duplicate inventory names), but
 * it never reads input, parses strings or prints. Display is the Printer's job.
 *
 * <p>It holds no state of its own. The caller owns the data and passes it in.
 *
 * <p>Location rules: {@code inv} commands only work outside an inventory, and {@code item}
 * commands only work inside one. {@code enter} works anywhere, switching directly if needed.
 * {@code help} works anywhere and changes nothing.
 * {@code stock} works anywhere: with a name it shows that inventory, without one the current inventory.
 */
public class Processor {
    /**
     * Logs at {@code FINE} and below only, so nothing is shown in the console by default.
     * Raise the level (e.g. in a {@code logging.properties} file) to trace what commands do.
     */
    private static final Logger LOGGER = Logger.getLogger(Processor.class.getName());
    /** Default count for item data when no {@code --count} is given. */
    private static final double DEFAULT_ITEM_COUNT = 1;
    /** Usage of {@code stock}, shown when it is run outside an inventory without a name. */
    private static final String STOCK_USAGE = "stock [NAME] [--below=COUNT]";
    /** Shown by {@code help}: every command, grouped by where it can be used. */
    private static final String HELP_MESSAGE = String.join("\n",
            "Commands (CAPS are values you fill in, [ ] is optional):",
            "Anywhere:",
            "  help - show this list",
            "  quit - exit StockHolm (also: exit, close, bye)",
            "  enter NAME - enter an inventory",
            "  stock [NAME] [--below=COUNT] - show stock levels, of the current inventory by default",
            "Outside an inventory:",
            "  inv add NAME - create an inventory",
            "  inv delete NAME - delete an inventory",
            "  inv list - list all inventories",
            "Inside an inventory:",
            "  back - leave the current inventory",
            "  item add NAME [--type=TYPE] [--count=COUNT] - add an item, or add to an existing one",
            "  item list - list the items",
            "  item delete INDEX - delete the item numbered in item list",
            "  import NAME [--type=TYPE] [--count=COUNT] - create an import order",
            "  export INDEX [--count=COUNT] - create an export order, reducing stock",
            "  order list - list all orders",
            "  approve INDEX - approve the order numbered in order list",
            "  deliver INDEX - mark the order numbered in order list as delivered");

    /**
     * Executes one command.
     *
     * @param command a command produced by the Parser
     * @param state   the application's data; the command may modify it
     * @return the message to display and whether the program should exit
     * @throws StockHolmException if the command breaks a business rule
     */
    public static Result process(Command command, AppState state) throws StockHolmException {
        switch (command.type()) {
        case NO_OP:
            return new Result("", false);
        case QUIT:
            return new Result("", true);
        case HELP:
            return new Result(HELP_MESSAGE, false);
        case INV_ADD:
            requireOutsideInventory(state);
            return addInventory(command.getArg(ArgKey.INV_NAME), state);
        case INV_DELETE:
            requireOutsideInventory(state);
            return deleteInventory(command.getArg(ArgKey.INV_NAME), state);
        case INV_LIST:
            requireOutsideInventory(state);
            return listInventories(state.getInventories());
        case ENTER:
            return enterInventory(command.getArg(ArgKey.INV_NAME), state);
        case BACK:
            return leaveInventory(state);
        case ITEM_ADD:
            return addItem(command, requireInsideInventory(state));
        case IMPORT:
            return createImportOrder(command, requireInsideInventory(state));
        case EXPORT:
            return createExportOrder(command, requireInsideInventory(state));
        case ITEM_LIST:
            return listItems(requireInsideInventory(state));
        case ORDER_LIST:
            return listOrders(requireInsideInventory(state));
        case APPROVE:
            return approveOrder(command, requireInsideInventory(state));
        case DELIVER:
            return deliverOrder(command, requireInsideInventory(state));
        case ITEM_DELETE:
            // The Parser has already checked that the index is a positive whole number.
            int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
            return deleteItem(index, requireInsideInventory(state));
        case STOCK:
            return showStock(command, state);
        default:
            throw new StockHolmException("Command not supported yet: " + command.type());
        }
    }

    private static Result addInventory(String name, AppState state) throws StockHolmException {
        if (state.findInventory(name) != null) {
            throw new StockHolmException("Inventory already exists: " + name);
        }
        state.getInventories().add(new Inventory(name));
        return new Result("Added inventory: " + name, false);
    }

    private static Result deleteInventory(String name, AppState state) throws StockHolmException {
        Inventory target = requireInventory(name, state);
        state.getInventories().remove(target);
        return new Result("Deleted inventory: " + name, false);
    }

    private static Result listInventories(List<Inventory> inventories) {
        if (inventories.isEmpty()) {
            return new Result("No inventories yet.", false);
        }
        StringBuilder message = new StringBuilder("Inventories:");
        for (int i = 0; i < inventories.size(); i++) {
            message.append("\n").append(i + 1).append(". ").append(inventories.get(i).getName());
        }
        return new Result(message.toString(), false);
    }

    /**
     * Returns the inventory with the given name, or throws if there is none.
     *
     * @throws StockHolmException if no inventory has that name
     */
    private static Inventory requireInventory(String name, AppState state) throws StockHolmException {
        assert name != null && !name.isEmpty() : "Parser should reject a missing inventory name";
        Inventory target = state.findInventory(name);
        if (target == null) {
            LOGGER.log(Level.FINE, "Rejected: no inventory named {0}", name);
            throw new StockHolmException("No such inventory: " + name);
        }
        return target;
    }

    private static Result enterInventory(String name, AppState state) throws StockHolmException {
        Inventory target = requireInventory(name, state);
        state.enterInventory(target);
        return new Result("Entered inventory: " + name, false);
    }

    private static Result leaveInventory(AppState state) throws StockHolmException {
        Inventory current = requireInsideInventory(state);
        state.leaveInventory();
        return new Result("Left inventory: " + current.getName(), false);
    }

    /**
     * Adds a new item, or merges into an existing item with the same name by adding the counts.
     * Merging is refused if a different {@code --type} is given, so a type is never silently lost.
     */
    private static Result addItem(Command command, Inventory inventory) throws StockHolmException {
        // Without --type the type is empty, which Inventory never treats as a conflict.
        Item item = new Item(command.getArg(ArgKey.ITEM_NAME), typeOrEmpty(command), countOrDefault(command));
        Item stored = inventory.addOrMerge(item);
        if (stored != item) {
            return new Result("Added " + Item.formatCount(item.getCount()) + " to existing item: " + stored, false);
        }
        return new Result("Added item: " + item, false);
    }

    /** Creates a pending import order with its own item; current stock is unchanged. */
    private static Result createImportOrder(Command command, Inventory inventory) {
        Item item = new Item(command.getArg(ArgKey.ITEM_NAME), typeOrEmpty(command), countOrDefault(command));
        ImportOrder order = new ImportOrder(item, inventory);
        inventory.addOrder(order);
        return formatOrderCreated(order);
    }

    /** Creates an export order and immediately reduces stock, removing items whose count reaches zero. */
    private static Result createExportOrder(Command command, Inventory inventory) throws StockHolmException {
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

    /** Describes a newly created order and its item. */
    private static Result formatOrderCreated(Order order) {
        Item item = order.getItem();
        return new Result(order.getKindLabel() + " created:\nItem name: " + item.getName()
                + "\nItem type: " + item.getType()
                + "\nItem count: " + Item.formatCount(item.getCount()), false);
    }

    /** Lists every order in creation order without changing orders or stock. */
    private static Result listOrders(Inventory inventory) {
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
    private static Result approveOrder(Command command, Inventory inventory) throws StockHolmException {
        int index = Integer.parseInt(command.getArg(ArgKey.ORDER_INDEX));
        requireOrder(index, inventory).approve();
        return new Result("Approved order " + index + " in " + inventory.getName() + ".", false);
    }

    /**
     * Marks the order at the one-based position shown by {@code order list} as delivered.
     * An import order's item is then added to stock; an export order's stock was already reduced on creation.
     */
    private static Result deliverOrder(Command command, Inventory inventory) throws StockHolmException {
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

    private static Result listItems(Inventory inventory) {
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

    /**
     * Deletes an item by its position in {@code item list}.
     *
     * @param index one-based position, as shown to the user
     */
    private static Result deleteItem(int index, Inventory inventory) throws StockHolmException {
        requireItem(index, inventory);
        Item removed = inventory.removeItem(index - 1);
        return new Result("Deleted item: " + removed, false);
    }

    /**
     * Shows every item of an inventory with its count, followed by the totals.
     * The inventory is the one named in the command, or the current one if no name is given,
     * so stock can be checked from anywhere without entering the inventory.
     * Items keep the numbers shown by {@code item list}.
     * With {@code --below=COUNT}, only items whose count is below {@code COUNT} are shown, to spot low stock.
     */
    private static Result showStock(Command command, AppState state) throws StockHolmException {
        Inventory inventory = requireNamedOrCurrentInventory(command, state, STOCK_USAGE);
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

    /** Returns the {@code --count} argument as a number, or the default count if none was given. */
    private static double countOrDefault(Command command) {
        return command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT))
                : DEFAULT_ITEM_COUNT;
    }

    /** Returns the {@code --type} argument, or an empty type if none was given. */
    private static String typeOrEmpty(Command command) {
        return command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : "";
    }

    /**
     * Returns the item at a one-based position in {@code item list}, or throws if there is none.
     *
     * @param index one-based position, as shown to the user
     */
    private static Item requireItem(int index, Inventory inventory) throws StockHolmException {
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
     */
    private static Order requireOrder(int index, Inventory inventory) throws StockHolmException {
        List<Order> orders = inventory.getOrders();
        if (index > orders.size()) {
            throw new StockHolmException("No order number " + index + ". " + inventory.getName()
                    + " has " + orders.size() + " order(s); use order list to see them.");
        }
        return orders.get(index - 1);
    }

    /** Throws if the user is inside an inventory, where {@code inv} commands are not allowed. */
    private static void requireOutsideInventory(AppState state) throws StockHolmException {
        if (state.isInsideInventory()) {
            throw new StockHolmException("You are inside " + state.getCurrentInventory().getName()
                    + ". Use back to leave it first.");
        }
    }

    /**
     * Returns the current inventory, or throws if the user is not inside one.
     * Used by commands that only make sense inside an inventory.
     */
    private static Inventory requireInsideInventory(AppState state) throws StockHolmException {
        if (!state.isInsideInventory()) {
            throw new StockHolmException("You are not inside an inventory. Use enter NAME first.");
        }
        return state.getCurrentInventory();
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
    private static Inventory requireNamedOrCurrentInventory(Command command, AppState state, String usage)
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
}
