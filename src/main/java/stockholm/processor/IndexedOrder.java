package stockholm.processor;

import stockholm.order.Order;

/**
 * An order paired with its original one-based position in the overall order history.
 * Shared by {@link OrderHandlers} and {@link ResultFormatter} so paginated and filtered views
 * retain each order's permanent index.
 */
record IndexedOrder(int index, Order order) {
}
