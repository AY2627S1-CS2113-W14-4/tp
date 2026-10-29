package stockholm.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.Order;
import stockholm.order.OrderHistory;
import stockholm.order.OrderState;

/**
 * Builds the multi-line messages the handlers return: numbered lists of inventories, items and orders,
 * stock levels, and the details of a new order. Handlers decide what to show; this class decides how it is laid
 * out, so every list is numbered and worded the same way.
 *
 * <p>It only builds strings. It never changes data and never prints; the Printer indents and prints the result.
 */
final class ResultFormatter {
    private ResultFormatter() {
    }

    /** Lists every inventory by name, or says there are none. */
    static String inventoryList(List<Inventory> inventories) {
        if (inventories.isEmpty()) {
            return "No inventories yet.";
        }
        StringBuilder message = new StringBuilder("Inventories:");
        for (int i = 0; i < inventories.size(); i++) {
            appendNumbered(message, i + 1, inventories.get(i).getName());
        }
        return message.toString();
    }

    /** Lists an inventory's items with their one-based numbers, or says it has none. */
    static String itemList(Inventory inventory) {
        List<Item> items = inventory.getItems();
        if (items.isEmpty()) {
            return noItems(inventory);
        }
        StringBuilder message = new StringBuilder("Items in " + inventory.getName() + ":");
        appendItems(message, items);
        return message.toString();
    }

    /** Says an inventory has no items yet. */
    static String noItems(Inventory inventory) {
        return "No items in " + inventory.getName() + " yet.";
    }

    /**
     * Lists every item of a non-empty inventory, followed by the number of items and units.
     *
     * @param totalUnits the sum of all item counts, already added up exactly by the caller
     */
    static String stockLevels(Inventory inventory, BigDecimal totalUnits) {
        List<Item> items = inventory.getItems();
        StringBuilder message = new StringBuilder("Stock levels in " + inventory.getName() + ":");
        for (int i = 0; i < items.size(); i++) {
            appendNumbered(message, i + 1, items.get(i) + lowStockMarker(items.get(i)));
        }
        message.append("\nTotal: ").append(items.size()).append(" item(s), ")
                .append(totalUnits.stripTrailingZeros().toPlainString()).append(" unit(s)");
        return message.toString();
    }

    /**
     * Lists the items below a threshold, keeping their {@code item list} numbers, or says there are none.
     *
     * @param threshold the threshold as typed, e.g. {@code "5"}
     * @param lowItems  the low items keyed by their one-based number, in list order
     */
    static String lowStock(Inventory inventory, String threshold, Map<Integer, Item> lowItems) {
        if (lowItems.isEmpty()) {
            return "No items in " + inventory.getName() + " below " + threshold + ".";
        }
        StringBuilder message = new StringBuilder("Items in " + inventory.getName() + " below " + threshold + ":");
        for (Map.Entry<Integer, Item> entry : lowItems.entrySet()) {
            appendNumbered(message, entry.getKey(), entry.getValue() + lowStockMarker(entry.getValue()));
        }
        return message.toString();
    }

    /**
     * Lists search matches grouped by inventory, keeping their {@code item list} numbers, or says there are none.
     *
     * @param query      the search as described to the user, e.g. {@code matching "pen"}
     * @param matchCount the total number of matches across all inventories
     * @param matches    inventories with at least one match, each with its matches keyed by one-based number
     */
    static String findResults(String query, int matchCount, Map<Inventory, Map<Integer, Item>> matches) {
        if (matchCount == 0) {
            return "No items " + query + ".";
        }
        StringBuilder message = new StringBuilder("Found " + matchCount + " item(s) " + query + ":");
        for (Map.Entry<Inventory, Map<Integer, Item>> inventoryMatches : matches.entrySet()) {
            message.append("\n").append(inventoryMatches.getKey().getName()).append(":");
            for (Map.Entry<Integer, Item> match : inventoryMatches.getValue().entrySet()) {
                message.append("\n  ").append(match.getKey()).append(". ").append(match.getValue());
            }
        }
        return message.toString();
    }

    /**
     * Lists the items below their minimum with how many to reorder, keeping their {@code item list} numbers,
     * or says there is nothing to restock.
     *
     * @param lowItems the low items keyed by their one-based number, in list order
     */
    static String restockList(Inventory inventory, Map<Integer, Item> lowItems) {
        if (lowItems.isEmpty()) {
            return "Nothing to restock in " + inventory.getName() + ".";
        }
        StringBuilder message = new StringBuilder("Items to restock in " + inventory.getName() + ":");
        for (Map.Entry<Integer, Item> entry : lowItems.entrySet()) {
            Item item = entry.getValue();
            appendNumbered(message, entry.getKey(), item + " (min " + Item.formatCount(item.getMinCount())
                    + ", reorder " + Item.formatCount(item.getShortfall()) + ")");
        }
        return message.toString();
    }

    /**
     * Describes an item that is below its minimum, e.g.
     * {@code Low stock: Blue Pen has 1 left, below its minimum of 5. Reorder at least 4.}
     */
    static String lowStockAlert(Item item) {
        assert item.isLowOnStock() : "Only items below their minimum get an alert";
        return "Low stock: " + item.getName() + " has " + Item.formatCount(item.getCount())
                + " left, below its minimum of " + Item.formatCount(item.getMinCount())
                + ". Reorder at least " + Item.formatCount(item.getShortfall()) + ".";
    }

    /** Returns {@code " [LOW: min M]"} for an item below its minimum, or an empty string otherwise. */
    private static String lowStockMarker(Item item) {
        return item.isLowOnStock() ? " [LOW: min " + Item.formatCount(item.getMinCount()) + "]" : "";
    }

    /** Lists every order in the history using default pagination (Page 1 of 10 per page). */
    static String orderList(OrderHistory history) {
        List<Order> orders = history.getOrders();
        if (orders.isEmpty()) {
            return "No orders yet.";
        }
        int totalPages = (int) (((long) orders.size() + 9) / 10);
        return orderList(orders, 1, totalPages, 10);
    }

    /**
     * Lists a page of orders with their kind, inventory, state and item, or says there are none.
     *
     * @param orders all orders in creation order
     * @param page the one-based page number to display
     * @param totalPages the total number of pages
     * @param pageSize the number of orders per page
     * @return formatted paginated order list
     */
    static String orderList(List<Order> orders, int page, int totalPages, int pageSize) {
        List<IndexedOrder> indexed = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); i++) {
            indexed.add(new IndexedOrder(i + 1, orders.get(i)));
        }
        return orderListIndexed(indexed, page, totalPages, pageSize);
    }

    /**
     * Lists a page of indexed orders with their kind, inventory, state and item, or says there are none.
     *
     * @param orders indexed orders matching filters
     * @param page the one-based page number to display
     * @param totalPages the total number of pages
     * @param pageSize the number of orders per page
     * @return formatted paginated order list
     */
    static String orderListIndexed(List<IndexedOrder> orders, int page, int totalPages, int pageSize) {
        if (orders.isEmpty()) {
            return "No orders yet.";
        }
        assert page >= 1 && page <= totalPages : "Page must be within 1 and totalPages";
        StringBuilder message = new StringBuilder("Orders (Page " + page + " of " + totalPages + "):");
        int startIndex = (int) Math.min((long) (page - 1) * pageSize, orders.size());
        int endIndex = (int) Math.min((long) startIndex + pageSize, orders.size());
        for (int i = startIndex; i < endIndex; i++) {
            IndexedOrder indexed = orders.get(i);
            Order order = indexed.order();
            Item item = order.getItem();
            appendNumbered(message, indexed.index(), order.getKindLabel() + inventoryLabel(order));
            message.append("\n   State: ").append(order.getState());
            if (order.getState() == OrderState.REJECTED && order.getReason() != null) {
                message.append("\n   Reason: ").append(order.getReason());
            }
            message.append("\n   Item name: ").append(item.getName())
                    .append("\n   Item type: ").append(item.getType())
                    .append("\n   Item count: ").append(Item.formatCount(item.getCount()));
        }
        return message.toString();
    }

    /** Names the inventory an order moves stock into or out of, e.g. {@code " (to Shop)"}, or nothing if none. */
    private static String inventoryLabel(Order order) {
        if (order.getSrc() != null && order.getDst() != null) {
            return " (from " + order.getSrc() + " to " + order.getDst() + ")";
        }
        if (order.getDst() != null) {
            return " (to " + order.getDst() + ")";
        }
        if (order.getSrc() != null) {
            return " (from " + order.getSrc() + ")";
        }
        return "";
    }

    /** Describes a newly created order and its item. */
    static String orderCreated(Order order) {
        Item item = order.getItem();
        return order.getKindLabel() + " created:\nItem name: " + item.getName()
                + "\nItem type: " + item.getType()
                + "\nItem count: " + Item.formatCount(item.getCount());
    }

    /** Appends every item on its own line, numbered from 1. */
    private static void appendItems(StringBuilder message, List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            appendNumbered(message, i + 1, items.get(i));
        }
    }

    /** Appends one numbered line, e.g. {@code "\n2. Pen x3"}. */
    private static void appendNumbered(StringBuilder message, int number, Object entry) {
        message.append("\n").append(number).append(". ").append(entry);
    }
}
