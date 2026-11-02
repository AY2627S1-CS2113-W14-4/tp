package stockholm.processor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.inventory.Inventory;
import stockholm.order.Order;
import stockholm.state.AppState;

/**
 * Shared state and helpers for the processor integration tests.
 * Each test gets a fresh, empty {@link AppState} so tests cannot affect one another.
 */
abstract class ProcessorTestBase {
    protected AppState state;

    /** Shortcut to {@code state.getInventories()}. */
    protected ArrayList<Inventory> inventories;

    @BeforeEach
    public void setUp() {
        state = new AppState();
        inventories = state.getInventories();
    }

    /** Builds an {@code inv} or {@code enter} command that takes an inventory name. */
    protected static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }

    /**
     * Builds an {@code item add} command.
     *
     * @param type  the {@code --type} value, or {@code null} to leave it out
     * @param count the {@code --count} value, or {@code null} to leave it out
     */
    protected static Command itemAdd(String name, String type, String count) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_NAME, name);
        if (type != null) {
            args.put(ArgKey.ITEM_TYPE, type);
        }
        if (count != null) {
            args.put(ArgKey.ITEM_COUNT, count);
        }
        return new Command(CommandType.ITEM_ADD, args);
    }

    /** Shortcut to every order in {@code state}'s order history. */
    protected List<Order> orders() {
        return state.getOrderHistory().getOrders();
    }

    /** Removes variable clock values when checking the rest of the order-list layout. */
    protected static Result withoutOrderTimestamps(Result result) {
        String message = result.message().replaceAll("(?m)^   (?:Created|Status changed) \\(UTC\\): [^\\n]+\\n", "");
        return new Result(message, false);
    }

    /** Creates an inventory named "Shop" and enters it. */
    protected Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }

    /** Builds a {@code stock} command; pass {@code null} to leave out the name or the threshold. */
    protected static Command stock(String inventoryName, String below) {
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (inventoryName != null) {
            args.put(ArgKey.INV_NAME, inventoryName);
        }
        if (below != null) {
            args.put(ArgKey.STOCK_BELOW, below);
        }
        return new Command(CommandType.STOCK, args);
    }
}
