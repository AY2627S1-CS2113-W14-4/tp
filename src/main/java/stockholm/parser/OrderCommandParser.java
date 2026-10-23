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
 * Parses commands about orders: {@code order list}, {@code import}, {@code export}, {@code approve},
 * {@code reject}, {@code cancel} and {@code deliver}.
 */
final class OrderCommandParser {
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
        String usage = CommandType.EXPORT.usage();
        String[] indexAndOptions = splitFirstWord(arguments);
        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_INDEX, requireIndex(indexAndOptions[0], "item number", usage));

        Map<String, String> options = parseOptions(indexAndOptions[1], usage, Set.of("count"));
        if (options.containsKey("count")) {
            args.put(ArgKey.ITEM_COUNT, requirePositiveDecimal(options.get("count"), "Count"));
        }
        return new Command(CommandType.EXPORT, args);
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
     *
     * @param arguments the text after {@code reject}
     * @return the parsed reject command
     * @throws StockHolmException if the order number or reason is missing or malformed
     */
    static Command parseReject(String arguments) throws StockHolmException {
        String usage = CommandType.REJECT.usage();
        String[] parts = splitFirstWord(arguments);
        String index = requireIndex(parts[0], "order number", usage);
        String reason = requireArg(parts[1], "rejection reason", usage);
        if (reason.startsWith("--reason=")) {
            String value = reason.substring("--reason=".length()).trim();
            if (value.startsWith("\"")) {
                if (!value.endsWith("\"") || value.length() < 2) {
                    throw new StockHolmException("Missing closing quote in rejection reason. Usage: " + usage);
                }
                value = value.substring(1, value.length() - 1).trim();
            }
            reason = requireArg(value, "rejection reason", usage);
        } else if (reason.startsWith("\"")) {
            if (!reason.endsWith("\"") || reason.length() < 2) {
                throw new StockHolmException("Missing closing quote in rejection reason. Usage: " + usage);
            }
            reason = requireArg(reason.substring(1, reason.length() - 1).trim(), "rejection reason", usage);
        }
        return new Command(CommandType.REJECT, Map.of(
                ArgKey.ORDER_INDEX, index,
                ArgKey.ORDER_REASON, reason));
    }
}
