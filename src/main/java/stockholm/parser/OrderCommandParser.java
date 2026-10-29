package stockholm.parser;

import static stockholm.parser.ArgumentParser.parseOptions;
import static stockholm.parser.ArgumentParser.requireArg;
import static stockholm.parser.ArgumentParser.requireIndex;
import static stockholm.parser.ArgumentParser.requirePositiveDecimal;
import static stockholm.parser.ArgumentParser.splitFirstWord;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Parses commands about orders: {@code order list}, {@code import}, {@code export}, {@code transfer}, {@code approve},
 * {@code reject}, {@code cancel} and {@code deliver}.
 */
final class OrderCommandParser {
    /** Optional prefix before a rejection reason, as in {@code reject 1 --reason="Damaged"}. */
    private static final String REASON_OPTION = "--reason=";

    private OrderCommandParser() {
    }

    /** Parses the part after {@code order}; listing takes no arguments. */
    static Command parseOrderCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        if (parts[0].equals("list") && parts[1].isEmpty()) {
            return new Command(CommandType.ORDER_LIST);
        }
        throw new StockHolmException("Usage: " + CommandType.ORDER_LIST.usage());
    }

    /**
     * Parses {@code import NAME [--type=TYPE] [--count=COUNT]}, which has the same syntax as {@code item add}.
     */
    static Command parseImport(String rest) throws StockHolmException {
        return ItemCommandParser.parseNameTypeCount(rest, CommandType.IMPORT);
    }

    /** Parses {@code INDEX [--count=COUNT]} with a positive count; stock is checked by the Processor. */
    static Command parseExport(String arguments) throws StockHolmException {
        return parseStockOrder(arguments, CommandType.EXPORT);
    }

    /** Parses a transfer's item index, optional count, and optional inventory names. */
    static Command parseTransfer(String arguments) throws StockHolmException {
        return parseStockOrder(arguments, CommandType.TRANSFER);
    }

    /** Shares index and count validation for orders reserving existing stock. */
    private static Command parseStockOrder(String arguments, CommandType type) throws StockHolmException {
        String usage = type.usage();
        String[] indexAndOptions = splitFirstWord(arguments);
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_INDEX, requireIndex(indexAndOptions[0], "item number", usage));

        Set<String> allowed = type == CommandType.TRANSFER ? Set.of("count", "from", "to") : Set.of("count");
        Map<String, String> options = parseOptions(indexAndOptions[1], usage, allowed);
        if (options.containsKey("count")) {
            args.put(ArgKey.ITEM_COUNT, requirePositiveDecimal(options.get("count"), "Count"));
        }
        if (options.containsKey("from")) {
            args.put(ArgKey.INV_FROM, requireArg(options.get("from").trim(), "source inventory name", usage));
        }
        if (options.containsKey("to")) {
            args.put(ArgKey.INV_TO, requireArg(options.get("to").trim(), "destination inventory name", usage));
        }
        return new Command(type, args);
    }

    /**
     * Parses the one-based order number displayed by {@code order list}, shared by {@code approve},
     * {@code cancel} and {@code deliver}.
     *
     * @param type the command to build; its usage is shown in error messages
     */
    static Command parseOrderIndex(String arguments, CommandType type) throws StockHolmException {
        return new Command(type, Map.of(ArgKey.ORDER_INDEX, requireIndex(arguments, "order number", type.usage())));
    }

    /**
     * Parses {@code reject INDEX REASON}: a one-based order number and a non-blank rejection reason.
     * The reason may be wrapped in quotes, and may also be given as {@code --reason=REASON}.
     *
     * @param arguments the text after {@code reject}
     * @return the parsed reject command
     * @throws StockHolmException if the order number or reason is missing or malformed
     */
    static Command parseReject(String arguments) throws StockHolmException {
        String usage = CommandType.REJECT.usage();
        String[] parts = splitFirstWord(arguments);
        String index = requireIndex(parts[0], "order number", usage);
        String reason = parts[1];
        if (reason.startsWith(REASON_OPTION)) {
            reason = reason.substring(REASON_OPTION.length()).trim();
        }
        if (reason.startsWith("\"")) {
            if (reason.length() < 2 || !reason.endsWith("\"")) {
                throw new StockHolmException("Missing closing quote in rejection reason. Usage: " + usage);
            }
            reason = reason.substring(1, reason.length() - 1).trim();
        }
        return new Command(CommandType.REJECT, Map.of(
                ArgKey.ORDER_INDEX, index,
                ArgKey.ORDER_REASON, requireArg(reason, "rejection reason", usage)));
    }
}
