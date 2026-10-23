package stockholm.parser;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import stockholm.exceptions.StockHolmException;
import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;

/**
 * Turns a raw input line into a {@link Command}.
 * It only checks syntax (e.g. a missing argument or an unknown command word).
 * It never looks at inventory data. Checking whether an inventory exists is the Processor's job.
 */
public class Parser {
    /** Logs at {@code FINE} only, so nothing is shown in the console by default. */
    private static final Logger LOGGER = Logger.getLogger(Parser.class.getName());
    /**
     * Maps alternate spellings of a command to its canonical form.
     * This is the single place to register new aliases.
     */
    private static final Map<String, String> ALIASES = Map.of(
            "close", "quit",
            "exit", "quit",
            "bye", "quit"
    );

    /**
     * Matches one option such as {@code --count=2} or {@code --type="Office Supplies"},
     * with optional leading whitespace. Group 1 is the option name; the value is in group 2
     * if it was quoted, otherwise in group 3.
     */
    private static final Pattern OPTION = Pattern.compile("\\s*--([a-z]+)=(?:\"([^\"]*)\"|([^\\s\"]+))");
    /** A positive decimal without sign or exponent, e.g. {@code 2} or {@code 2.5}. */
    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");

    /**
     * Parses one line of user input.
     *
     * @param rawInput the line exactly as the user typed it
     * @return the parsed command; blank input gives {@link CommandType#NO_OP}
     * @throws StockHolmException if the command word is unknown or arguments are missing or malformed
     */
    public static Command parseCommand(String rawInput) throws StockHolmException {
        String[] parts = splitFirstWord(rawInput);
        String commandWord = ALIASES.getOrDefault(parts[0], parts[0]);
        String rest = parts[1];

        switch (commandWord) {
        case "":
            return new Command(CommandType.NO_OP);
        case "quit":
            return new Command(CommandType.QUIT);
        case "help":
            return parseHelp(rest);
        case "inv":
            return parseInvCommand(rest);
        case "enter":
            return new Command(CommandType.ENTER,
                    Map.of(ArgKey.INV_NAME, requireArg(rest, "inventory name", CommandType.ENTER.usage())));
        case "back":
            return new Command(CommandType.BACK);
        case "item":
            return parseItemCommand(rest);
        case "order":
            return parseOrderCommand(rest);
        case "import":
            return parseNameTypeCount(rest, CommandType.IMPORT);
        case "export":
            return parseExport(rest);
        case "approve":
            return parseOrderIndex(rest, CommandType.APPROVE);
        case "deliver":
            return parseOrderIndex(rest, CommandType.DELIVER);
        case "stock":
            return parseStock(rest);
        default:
            throw new StockHolmException("Unknown command: " + commandWord);
        }
    }

    /**
     * Splits a string into its first word and the trimmed remainder. Words may be separated by any whitespace.
     *
     * @return a two-element array {@code {firstWord, rest}}; either may be empty
     */
    private static String[] splitFirstWord(String string) {
        String[] parts = string.trim().split("\\s+", 2);
        String rest = (parts.length == 2) ? parts[1] : "";
        return new String[]{parts[0], rest};
    }

    /** Parses {@code help}, which takes no arguments. */
    private static Command parseHelp(String rest) throws StockHolmException {
        if (!rest.isEmpty()) {
            throw new StockHolmException("Usage: " + CommandType.HELP.usage());
        }
        return new Command(CommandType.HELP);
    }

    /** Parses the part after {@code inv}, e.g. {@code "add Shop"} or {@code "list"}. */
    private static Command parseInvCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        String subcommand = parts[0];
        String name = parts[1];

        switch (subcommand) {
        case "add":
            return new Command(CommandType.INV_ADD,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", CommandType.INV_ADD.usage())));
        case "delete":
            return new Command(CommandType.INV_DELETE,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", CommandType.INV_DELETE.usage())));
        case "list":
            return new Command(CommandType.INV_LIST);
        default:
            throw new StockHolmException("Usage: inv add|delete|list [NAME]");
        }
    }

    /** Parses the part after {@code item}, e.g. {@code "add Pen --count=3"} or {@code "delete 2"}. */
    private static Command parseItemCommand(String rest) throws StockHolmException {
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
        default:
            throw new StockHolmException("Usage: item add|list|delete ...");
        }
    }

    /** Parses the part after {@code order}; listing takes no arguments. */
    private static Command parseOrderCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        if (parts[0].equals("list") && parts[1].isEmpty()) {
            return new Command(CommandType.ORDER_LIST);
        }
        throw new StockHolmException("Usage: " + CommandType.ORDER_LIST.usage());
    }

    /**
     * Parses {@code NAME [--type=TYPE] [--count=COUNT]}, shared by {@code item add} and {@code import}.
     * The name may be quoted to contain spaces ({@code "A4 Paper Case"}); an unquoted name runs until the first option.
     *
     * @param type the command to build; its usage is shown in error messages
     */
    private static Command parseNameTypeCount(String arguments, CommandType type) throws StockHolmException {
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

    /** Parses {@code INDEX [--count=COUNT]} with a positive count; stock is checked by the Processor. */
    private static Command parseExport(String arguments) throws StockHolmException {
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
     * Parses the one-based order number displayed by {@code order list}, shared by {@code approve} and
     * {@code deliver}.
     *
     * @param type the command to build; its usage is shown in error messages
     */
    private static Command parseOrderIndex(String arguments, CommandType type) throws StockHolmException {
        return new Command(type, Map.of(ArgKey.ORDER_INDEX, requireIndex(arguments, "order number", type.usage())));
    }

    /**
     * Splits arguments into a leading name and the options text after it.
     * The name may be quoted to contain spaces ({@code "A4 Paper Case"}); an unquoted name runs
     * until the first {@code --}.
     *
     * @param arguments the text after the command word(s)
     * @param label     what the name is, used in the error message (e.g. "item name")
     * @param usage     the correct syntax, shown in the error message
     * @return a two-element array {@code {name, optionsText}}; either may be empty
     * @throws StockHolmException if a quoted name has no closing quote
     */
    private static String[] splitNameAndOptions(String arguments, String label, String usage)
            throws StockHolmException {
        assert arguments != null : "splitFirstWord never gives null";
        assert arguments.equals(arguments.trim()) : "Arguments are trimmed before splitting";
        if (arguments.startsWith("\"")) {
            int closingQuote = arguments.indexOf('"', 1);
            if (closingQuote == -1) {
                throw new StockHolmException("Missing closing quote in " + label + ". Usage: " + usage);
            }
            return new String[]{arguments.substring(1, closingQuote).trim(), arguments.substring(closingQuote + 1)};
        }
        int optionStart = arguments.indexOf("--");
        String name = (optionStart == -1) ? arguments : arguments.substring(0, optionStart).trim();
        String optionsText = (optionStart == -1) ? "" : arguments.substring(optionStart);
        return new String[]{name, optionsText};
    }

    /**
     * Parses {@code [NAME] [--below=COUNT]}: an optional inventory name, which may be quoted, and an
     * optional low-stock threshold. Without a name the command refers to the current inventory;
     * the Processor checks that there is one.
     */
    private static Command parseStock(String arguments) throws StockHolmException {
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

    /** Parses {@code INDEX}: a one-based whole number. Whether the item exists is checked later by the Processor. */
    private static Command parseItemDelete(String arguments) throws StockHolmException {
        return new Command(CommandType.ITEM_DELETE,
                Map.of(ArgKey.ITEM_INDEX, requireIndex(arguments, "item number", CommandType.ITEM_DELETE.usage())));
    }

    /**
     * Returns {@code value} if it is a one-based whole number that fits in an {@code int}. Otherwise throws.
     *
     * @param value the number as typed
     * @param label what the number is, in lower case, used in error messages (e.g. "item number")
     * @param usage the correct syntax, shown if the number is missing
     */
    private static String requireIndex(String value, String label, String usage) throws StockHolmException {
        requireArg(value, label, usage);
        if (!WHOLE_NUMBER.matcher(value).matches() || isZeroOrTooLarge(value)) {
            String capitalized = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            throw new StockHolmException(capitalized + " must be a positive whole number: " + value);
        }
        return value;
    }

    /**
     * Returns {@code value} if it is a positive decimal such as {@code 2} or {@code 2.5}. Otherwise throws.
     * Numbers too large to fit in a finite {@code double} are rejected too.
     *
     * @param value the number as typed
     * @param label what the number is, used at the start of the error message (e.g. "Count")
     */
    private static String requirePositiveDecimal(String value, String label) throws StockHolmException {
        if (!DECIMAL.matcher(value).matches() || Double.parseDouble(value) <= 0
                || !Double.isFinite(Double.parseDouble(value))) {
            throw new StockHolmException(label + " must be a positive number, e.g. 2 or 2.5: " + value);
        }
        return value;
    }

    /** Returns {@code true} if a string of digits is 0 or does not fit in an {@code int}. */
    private static boolean isZeroOrTooLarge(String digits) {
        try {
            return Integer.parseInt(digits) == 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Parses a sequence of {@code --name=value} options.
     *
     * @param text    the options, possibly empty
     * @param usage   usage hint for error messages
     * @param allowed the option names this command accepts
     * @return option names mapped to their values (quotes removed), in the order given
     * @throws StockHolmException if the text is not a sequence of options, an option is repeated,
     *                            or an option is not in {@code allowed}
     */
    private static Map<String, String> parseOptions(String text, String usage, Set<String> allowed)
            throws StockHolmException {
        Map<String, String> options = new LinkedHashMap<>();
        Matcher matcher = OPTION.matcher(text);
        int position = 0;
        while (!text.substring(position).isBlank()) {
            matcher.region(position, text.length());
            if (!matcher.lookingAt()) {
                throw new StockHolmException("Invalid option: " + text.substring(position).trim()
                        + ". Usage: " + usage);
            }
            String value = (matcher.group(2) != null) ? matcher.group(2) : matcher.group(3);
            if (options.put(matcher.group(1), value) != null) {
                throw new StockHolmException("Option given more than once: --" + matcher.group(1));
            }
            position = matcher.end();
        }
        for (String name : options.keySet()) {
            if (!allowed.contains(name)) {
                throw new StockHolmException("Unknown option --" + name + ". Usage: " + usage);
            }
        }
        return options;
    }

    /**
     * Returns {@code value} if it's non-empty. Otherwise throws with a usage hint.
     *
     * @param value the argument as typed
     * @param label what the argument is, used in the error message (e.g. "inventory name")
     * @param usage the correct syntax, shown in the error message
     */
    private static String requireArg(String value, String label, String usage) throws StockHolmException {
        if (value.isEmpty()) {
            throw new StockHolmException("Missing " + label + ". Usage: " + usage);
        }
        return value;
    }
}
