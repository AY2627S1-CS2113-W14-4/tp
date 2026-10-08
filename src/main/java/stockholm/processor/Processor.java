package stockholm.processor;

import java.util.ArrayList;
import java.util.List;

import stockholm.exceptions.StockHolmException;
import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
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
 * and {@code order} commands only work inside one. {@code enter} works anywhere, switching directly if needed.
 */
public class Processor {
    /** Default count for item data when no {@code --count} is given. */
    private static final double DEFAULT_ITEM_COUNT = 1;

    /**
     * Executes one command.
     *
     * @param command a command produced by the Parser
     * @param state   the application's data; the command may modify it
     * @return the message to display and whether the program should exit
     * @throws StockHolmException if the command breaks a business rule
     */
    public static Result process(Command command, AppState state) throws StockHolmException {
        ArrayList<Inventory> inventories = state.getInventories();
        switch (command.type()) {
        case NO_OP:
            return new Result("", false);
        case QUIT:
            return new Result("", true);
        case INV_ADD:
            requireOutsideInventory(state);
            return addInventory(command.getArg(ArgKey.INV_NAME), inventories);
        case INV_DELETE:
            requireOutsideInventory(state);
            return deleteInventory(command.getArg(ArgKey.INV_NAME), inventories);
        case INV_LIST:
            requireOutsideInventory(state);
            return listInventories(inventories);
        case ENTER:
            return enterInventory(command.getArg(ArgKey.INV_NAME), state);
        case BACK:
            return leaveInventory(state);
        case ITEM_ADD:
            return addItem(command, requireInsideInventory(state));
        case IMPORT:
            return createImportOrder(command, requireInsideInventory(state));
        case ITEM_LIST:
            return listItems(requireInsideInventory(state));
        case ORDER_LIST:
            return listOrders(requireInsideInventory(state));
        case ITEM_DELETE:
            // The Parser has already checked that the index is a positive whole number.
            int index = Integer.parseInt(command.getArg(ArgKey.ITEM_INDEX));
            return deleteItem(index, requireInsideInventory(state));
        default:
            throw new StockHolmException("Command not supported yet: " + command.type());
        }
    }

    private static Result addInventory(String name, ArrayList<Inventory> inventories)
            throws StockHolmException {
        if (findInventory(name, inventories) != null) {
            throw new StockHolmException("Inventory already exists: " + name);
        }
        inventories.add(new Inventory(name));
        return new Result("Added inventory: " + name, false);
    }

    private static Result deleteInventory(String name, ArrayList<Inventory> inventories)
            throws StockHolmException {
        Inventory target = findInventory(name, inventories);
        if (target == null) {
            throw new StockHolmException("No such inventory: " + name);
        }
        inventories.remove(target);
        return new Result("Deleted inventory: " + name, false);
    }

    private static Result listInventories(ArrayList<Inventory> inventories) {
        if (inventories.isEmpty()) {
            return new Result("No inventories yet.", false);
        }
        StringBuilder message = new StringBuilder("Inventories:");
        for (int i = 0; i < inventories.size(); i++) {
            message.append("\n").append(i + 1).append(". ").append(inventories.get(i).getName());
        }
        return new Result(message.toString(), false);
    }

    /** Returns the inventory with the given name, or {@code null} if there is none. */
    private static Inventory findInventory(String name, ArrayList<Inventory> inventories) {
        for (Inventory inventory : inventories) {
            if (inventory.getName().equals(name)) {
                return inventory;
            }
        }
        return null;
    }

    private static Result enterInventory(String name, AppState state) throws StockHolmException {
        Inventory target = findInventory(name, state.getInventories());
        if (target == null) {
            throw new StockHolmException("No such inventory: " + name);
        }
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
        String name = command.getArg(ArgKey.ITEM_NAME);
        double count = command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT))
                : DEFAULT_ITEM_COUNT;

        Item existing = inventory.findItem(name);
        if (existing != null) {
            if (command.hasArg(ArgKey.ITEM_TYPE) && !command.getArg(ArgKey.ITEM_TYPE).equals(existing.getType())) {
                throw new StockHolmException("Item already exists with a different type: " + existing
                        + ". Leave out --type or use the same type.");
            }
            existing.addCount(count);
            return new Result("Added " + Item.formatCount(count) + " to existing item: " + existing, false);
        }

        String type = command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : "";
        Item item = new Item(name, type, count);
        inventory.addItem(item);
        return new Result("Added item: " + item, false);
    }

    /** Creates a pending import order with its own item; current stock is unchanged. */
    private static Result createImportOrder(Command command, Inventory inventory) {
        String name = command.getArg(ArgKey.ITEM_NAME);
        String type = command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : "";
        double count = command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT))
                : DEFAULT_ITEM_COUNT;
        Item item = new Item(name, type, count);
        inventory.addOrder(new ImportOrder(item));
        return new Result("Import order created:\nItem name: " + item.getName()
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
            String kind = order instanceof ImportOrder ? "Import order" : "Order";
            message.append("\n").append(i + 1).append(". ").append(kind)
                    .append("\n   State: ").append(order.getState())
                    .append("\n   Item name: ").append(item.getName())
                    .append("\n   Item type: ").append(item.getType())
                    .append("\n   Item count: ").append(Item.formatCount(item.getCount()));
        }
        return new Result(message.toString(), false);
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
        int itemCount = inventory.getItems().size();
        if (index > itemCount) {
            throw new StockHolmException("No item number " + index + ". " + inventory.getName()
                    + " has " + itemCount + " item(s); use item list to see them.");
        }
        Item removed = inventory.removeItem(index - 1);
        return new Result("Deleted item: " + removed, false);
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
}
