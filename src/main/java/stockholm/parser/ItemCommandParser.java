package stockholm.parser;

import static stockholm.parser.ArgumentParser.parseOptions;
import static stockholm.parser.ArgumentParser.requireArg;
import static stockholm.parser.ArgumentParser.requireIndex;
import static stockholm.parser.ArgumentParser.requirePositiveDecimal;
import static stockholm.parser.ArgumentParser.requireZeroOrPositiveDecimal;
import static stockholm.parser.ArgumentParser.splitFirstWord;
import static stockholm.parser.ArgumentParser.splitNameAndOptions;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Parses commands about the items in an inventory: {@code item add|list|delete|min}, {@code stock},
 * {@code find} and {@code restock}.
 */
final class ItemCommandParser {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(ItemCommandParser.class.getName());

    private ItemCommandParser() {
    }

    /** Parses the part after {@code item}, e.g. {@code "add Pen --count=3"} or {@code "delete 2"}. */
    static Command parseItemCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        String subcommand = parts[0];
        String arguments = parts[1];

        switch (subcommand) {
        case "add":
            return parseNameTypeCount(arguments, CommandType.ITEM_ADD);
        case "list":
            return new Command(CommandType.ITEM_LIST);
        case "delete":
            return parseItemDelete(arguments);
        case "min":
            return parseItemMin(arguments);
        default:
            throw new StockHolmException("Usage: item add|list|delete|min ...");
        }
    }

    /**
     * Parses {@code NAME [--type=TYPE] [--count=COUNT]}, shared by {@code item add} and {@code import}.
     * The name may be quoted to contain spaces ({@code "A4 Paper Case"}); an unquoted name runs until the first option.
     *
     * @param type the command to build; its usage is shown in error messages
     */
    static Command parseNameTypeCount(String arguments, CommandType type) throws StockHolmException {
        String usage = type.usage();
        String[] nameAndOptions = splitNameAndOptions(arguments, "item name", usage);
        String name = nameAndOptions[0];
        String optionsText = nameAndOptions[1];

        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_NAME, requireArg(name, "item name", usage));

        Map<String, String> options = parseOptions(optionsText, usage, Set.of("type", "count"));
        if (options.containsKey("type")) {
            args.put(ArgKey.ITEM_TYPE, requireArg(options.get("type").trim(), "item type", usage));
        }
        if (options.containsKey("count")) {
            args.put(ArgKey.ITEM_COUNT, requirePositiveDecimal(options.get("count"), "Count"));
        }
        return new Command(type, args);
    }

    /** Parses {@code INDEX}: a one-based whole number. Whether the item exists is checked later by the Processor. */
    private static Command parseItemDelete(String arguments) throws StockHolmException {
        return new Command(CommandType.ITEM_DELETE,
                Map.of(ArgKey.ITEM_INDEX, requireIndex(arguments, "item number", CommandType.ITEM_DELETE.usage())));
    }

    /**
     * Parses {@code [NAME] [--below=COUNT]}: an optional inventory name, which may be quoted, and an
     * optional low-stock threshold. Without a name the command refers to the current inventory;
     * the Processor checks that there is one.
     */
    static Command parseStock(String arguments) throws StockHolmException {
        String usage = CommandType.STOCK.usage();
        String[] nameAndOptions = splitNameAndOptions(arguments, "inventory name", usage);
        String name = nameAndOptions[0];

        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (!name.isEmpty()) {
            args.put(ArgKey.INV_NAME, name);
        }

        Map<String, String> options = parseOptions(nameAndOptions[1], usage, Set.of("below"));
        if (options.containsKey("below")) {
            args.put(ArgKey.STOCK_BELOW, requirePositiveDecimal(options.get("below"), "Threshold"));
        }
        assert !args.containsKey(ArgKey.INV_NAME) || !args.get(ArgKey.INV_NAME).isBlank()
                : "A stock inventory name is either absent or non-blank";
        LOGGER.log(Level.FINE, "Parsed stock command with arguments {0}", args);
        return new Command(CommandType.STOCK, args);
    }

    /**
     * Parses {@code [KEYWORD] [--type=TYPE]}. The keyword may contain spaces and may be quoted.
     * At least one of the two must be given, so a search never silently lists everything.
     */
    static Command parseFind(String arguments) throws StockHolmException {
        String usage = CommandType.FIND.usage();
        String[] keywordAndOptions = splitNameAndOptions(arguments, "search keyword", usage);
        String keyword = keywordAndOptions[0];

        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (!keyword.isEmpty()) {
            args.put(ArgKey.FIND_KEYWORD, keyword);
        }
        Map<String, String> options = parseOptions(keywordAndOptions[1], usage, Set.of("type"));
        if (options.containsKey("type")) {
            args.put(ArgKey.ITEM_TYPE, requireArg(options.get("type").trim(), "item type", usage));
        }
        if (args.isEmpty()) {
            throw new StockHolmException("Give a keyword, a type, or both. Usage: " + usage);
        }
        LOGGER.log(Level.FINE, "Parsed find command with arguments {0}", args);
        return new Command(CommandType.FIND, args);
    }

    /**
     * Parses {@code INDEX COUNT}: a one-based item number and a minimum stock level, which is {@code 0}
     * (to clear it) or a positive decimal. Whether the item exists is checked by the Processor.
     */
    private static Command parseItemMin(String arguments) throws StockHolmException {
        String usage = CommandType.ITEM_MIN.usage();
        String[] indexAndCount = splitFirstWord(arguments);
        String index = requireIndex(indexAndCount[0], "item number", usage);
        String count = requireArg(indexAndCount[1], "minimum count", usage);
        requireZeroOrPositiveDecimal(count, "Minimum count");
        LOGGER.log(Level.FINE, "Parsed item min for item {0} with minimum {1}", new Object[]{index, count});
        return new Command(CommandType.ITEM_MIN, Map.of(ArgKey.ITEM_INDEX, index, ArgKey.ITEM_MIN, count));
    }

    /** Parses {@code [NAME]}: an optional inventory name, which may be quoted. */
    static Command parseRestock(String arguments) throws StockHolmException {
        String usage = CommandType.RESTOCK.usage();
        String[] nameAndOptions = splitNameAndOptions(arguments, "inventory name", usage);
        if (!nameAndOptions[1].isBlank()) {
            throw new StockHolmException("restock takes no options. Usage: " + usage);
        }
        String name = nameAndOptions[0];
        if (name.isEmpty()) {
            return new Command(CommandType.RESTOCK);
        }
        return new Command(CommandType.RESTOCK, Map.of(ArgKey.INV_NAME, name));
    }
}
