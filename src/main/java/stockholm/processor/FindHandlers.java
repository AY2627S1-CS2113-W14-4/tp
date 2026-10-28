package stockholm.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.state.AppState;

/** Handles {@code find [KEYWORD] [--type=TYPE]}, which works anywhere and only reads data. */
final class FindHandlers {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(FindHandlers.class.getName());

    private FindHandlers() {
    }

    /**
     * Lists the items whose name contains the keyword and/or whose type equals the given type, ignoring case.
     * Inside an inventory only that inventory is searched; outside, every inventory is, so an item can be
     * found without knowing where it is kept. Matches keep their {@code item list} numbers.
     */
    static Result find(Command command, AppState state) {
        String keyword = command.hasArg(ArgKey.FIND_KEYWORD) ? command.getArg(ArgKey.FIND_KEYWORD) : null;
        String type = command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : null;
        assert keyword != null || type != null : "The Parser requires a keyword or a type";

        List<Inventory> searched = state.isInsideInventory()
                ? List.of(state.getCurrentInventory())
                : state.getInventories();
        // Inventories with at least one match, in search order, each with its matches keyed by item number.
        Map<Inventory, Map<Integer, Item>> matches = new LinkedHashMap<>();
        int matchCount = 0;
        for (Inventory inventory : searched) {
            Map<Integer, Item> found = findMatchesIn(inventory, keyword, type);
            if (!found.isEmpty()) {
                matches.put(inventory, found);
                matchCount += found.size();
            }
        }

        String query = describeQuery(keyword, type);
        LOGGER.log(Level.FINE, "find {0} searched {1} inventory(ies), {2} match(es)",
                new Object[]{query, searched.size(), matchCount});
        return new Result(ResultFormatter.findResults(query, matchCount, matches), false);
    }

    /** Returns the matching items of an inventory keyed by their one-based number, in {@code item list} order. */
    private static Map<Integer, Item> findMatchesIn(Inventory inventory, String keyword, String type) {
        String lowerKeyword = (keyword == null) ? null : keyword.toLowerCase(Locale.ROOT);
        List<Item> items = inventory.getItems();
        Map<Integer, Item> found = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean nameMatches = lowerKeyword == null
                    || item.getName().toLowerCase(Locale.ROOT).contains(lowerKeyword);
            boolean typeMatches = type == null || item.getType().equalsIgnoreCase(type);
            if (nameMatches && typeMatches) {
                found.put(i + 1, item);
            }
        }
        return found;
    }

    /** Describes a search for messages, e.g. {@code matching "pen" of type Stationery}. */
    private static String describeQuery(String keyword, String type) {
        List<String> parts = new ArrayList<>();
        if (keyword != null) {
            parts.add("matching \"" + keyword + "\"");
        }
        if (type != null) {
            parts.add("of type " + type);
        }
        return String.join(" ", parts);
    }
}
