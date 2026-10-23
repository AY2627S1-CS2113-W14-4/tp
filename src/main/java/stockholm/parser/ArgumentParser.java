package stockholm.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import stockholm.exceptions.StockHolmException;

/**
 * Shared helpers for reading the arguments of a command: splitting words, reading a (quoted) name and
 * {@code --name=value} options, and checking required values and numbers.
 * The command parsers use these so every command reports syntax errors the same way.
 */
final class ArgumentParser {
    /**
     * Matches one option such as {@code --count=2} or {@code --type="Office Supplies"},
     * with optional leading whitespace. Group 1 is the option name; the value is in group 2
     * if it was quoted, otherwise in group 3.
     */
    private static final Pattern OPTION = Pattern.compile("\\s*--([a-z]+)=(?:\"([^\"]*)\"|([^\\s\"]+))");
    /** A positive decimal without sign or exponent, e.g. {@code 2} or {@code 2.5}. */
    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");
    /** A whole number without sign, e.g. {@code 3}; zero and overly large values are rejected separately. */
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");

    private ArgumentParser() {
    }

    /**
     * Splits a string into its first word and the trimmed remainder. Words may be separated by any whitespace.
     *
     * @return a two-element array {@code {firstWord, rest}}; either may be empty
     */
    static String[] splitFirstWord(String string) {
        String[] parts = string.trim().split("\\s+", 2);
        String rest = (parts.length == 2) ? parts[1] : "";
        return new String[]{parts[0], rest};
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
    static String[] splitNameAndOptions(String arguments, String label, String usage)
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
     * Parses a sequence of {@code --name=value} options.
     *
     * @param text    the options, possibly empty
     * @param usage   usage hint for error messages
     * @param allowed the option names this command accepts
     * @return option names mapped to their values (quotes removed), in the order given
     * @throws StockHolmException if the text is not a sequence of options, an option is repeated,
     *                            or an option is not in {@code allowed}
     */
    static Map<String, String> parseOptions(String text, String usage, Set<String> allowed)
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
    static String requireArg(String value, String label, String usage) throws StockHolmException {
        if (value.isEmpty()) {
            throw new StockHolmException("Missing " + label + ". Usage: " + usage);
        }
        return value;
    }

    /**
     * Returns {@code value} if it is a one-based whole number that fits in an {@code int}. Otherwise throws.
     *
     * @param value the number as typed
     * @param label what the number is, in lower case, used in error messages (e.g. "item number")
     * @param usage the correct syntax, shown if the number is missing
     */
    static String requireIndex(String value, String label, String usage) throws StockHolmException {
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
    static String requirePositiveDecimal(String value, String label) throws StockHolmException {
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
}
