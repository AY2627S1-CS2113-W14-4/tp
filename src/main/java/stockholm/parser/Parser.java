package stockholm.parser;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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
    /**
     * Maps alternate spellings of a command to its canonical form.
     * This is the single place to register new aliases.
     */
    private static final Map<String, String> ALIASES = Map.of(
            "close", "quit",
            "exit", "quit",
            "bye", "quit"
    );

    private static final String ITEM_ADD_USAGE = "item add NAME [--type=TYPE] [--count=COUNT]";
    private static final String ITEM_DELETE_USAGE = "item delete INDEX";
    private static final String STOCK_USAGE = "stock [NAME] [--below=COUNT]";

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
        case "inv":
            return parseInvCommand(rest);
        case "enter":
            return new Command(CommandType.ENTER,
                    Map.of(ArgKey.INV_NAME, requireArg(rest, "inventory name", "enter NAME")));
        case "back":
            return new Command(CommandType.BACK);
        case "item":
            return parseItemCommand(rest);
        case "stock":
            return parseStock(rest);
        default:
            throw new StockHolmException("Unknown command: " + commandWord);
        }
    }

    /**
     * Splits a string into its first word and the trimmed remainder.
     *
     * @return a two-element array {@code {firstWord, rest}}; either may be empty
     */
    private static String[] splitFirstWord(String string) {
        String trimmed = string.trim();
        int firstSpace = trimmed.indexOf(' ');
        String firstWord = (firstSpace == -1) ? trimmed : trimmed.substring(0, firstSpace);
        String rest = (firstSpace == -1) ? "" : trimmed.substring(firstSpace + 1).trim();
        return new String[]{firstWord, rest};
    }

    /** Parses the part after {@code inv}, e.g. {@code "add Shop"} or {@code "list"}. */
    private static Command parseInvCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        String subcommand = parts[0];
        String name = parts[1];

        switch (subcommand) {
        case "add":
            return new Command(CommandType.INV_ADD,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", "inv add NAME")));
        case "delete":
            return new Command(CommandType.INV_DELETE,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", "inv delete NAME")));
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
            return parseItemAdd(arguments);
        case "list":
            return new Command(CommandType.ITEM_LIST);
        case "delete":
            return parseItemDelete(arguments);
        default:
            throw new StockHolmException("Usage: item add|list|delete ...");
        }
    }

    /**
     * Parses {@code NAME [--type=TYPE] [--count=COUNT]}. The name may be quoted to contain spaces
     * ({@code "A4 Paper Case"}); an unquoted name runs until the first option.
     */
    private static Command parseItemAdd(String arguments) throws StockHolmException {
        String[] nameAndOptions = splitNameAndOptions(arguments, "item name", ITEM_ADD_USAGE);
        String name = nameAndOptions[0];
        String optionsText = nameAndOptions[1];

        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        args.put(ArgKey.ITEM_NAME, requireArg(name, "item name", ITEM_ADD_USAGE));

        Map<String, String> options = parseOptions(optionsText, ITEM_ADD_USAGE);
        for (Map.Entry<String, String> option : options.entrySet()) {
            String value = option.getValue();
            switch (option.getKey()) {
            case "type":
                args.put(ArgKey.ITEM_TYPE, requireArg(value.trim(), "item type", ITEM_ADD_USAGE));
                break;
            case "count":
                args.put(ArgKey.ITEM_COUNT, requirePositiveDecimal(value, "Count"));
                break;
            default:
                throw new StockHolmException("Unknown option --" + option.getKey() + ". Usage: " + ITEM_ADD_USAGE);
            }
        }
        return new Command(CommandType.ITEM_ADD, args);
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
        String[] nameAndOptions = splitNameAndOptions(arguments, "inventory name", STOCK_USAGE);
        String name = nameAndOptions[0];

        Map<ArgKey, String> args = new EnumMap<>(ArgKey.class);
        if (!name.isEmpty()) {
            args.put(ArgKey.INV_NAME, name);
        }

        Map<String, String> options = parseOptions(nameAndOptions[1], STOCK_USAGE);
        for (Map.Entry<String, String> option : options.entrySet()) {
            if (!option.getKey().equals("below")) {
                throw new StockHolmException("Unknown option --" + option.getKey() + ". Usage: " + STOCK_USAGE);
            }
            args.put(ArgKey.STOCK_BELOW, requirePositiveDecimal(option.getValue(), "Threshold"));
        }
        return new Command(CommandType.STOCK, args);
    }

    /** Parses {@code INDEX}: a one-based whole number. Whether the item exists is checked later by the Processor. */
    private static Command parseItemDelete(String arguments) throws StockHolmException {
        String index = requireArg(arguments, "item number", ITEM_DELETE_USAGE);
        if (!WHOLE_NUMBER.matcher(index).matches() || isZeroOrTooLarge(index)) {
            throw new StockHolmException("Item number must be a positive whole number: " + index);
        }
        return new Command(CommandType.ITEM_DELETE, Map.of(ArgKey.ITEM_INDEX, index));
    }

    /**
     * Returns {@code value} if it is a positive decimal such as {@code 2} or {@code 2.5}. Otherwise throws.
     *
     * @param value the number as typed
     * @param label what the number is, used at the start of the error message (e.g. "Count")
     */
    private static String requirePositiveDecimal(String value, String label) throws StockHolmException {
        if (!DECIMAL.matcher(value).matches() || Double.parseDouble(value) <= 0) {
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
     * @param text  the options, possibly empty
     * @param usage usage hint for error messages
     * @return option names mapped to their values (quotes removed)
     * @throws StockHolmException if the text is not a sequence of options, or an option is repeated
     */
    private static Map<String, String> parseOptions(String text, String usage) throws StockHolmException {
        Map<String, String> options = new HashMap<>();
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
