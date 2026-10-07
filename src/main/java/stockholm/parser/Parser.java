package stockholm.parser;

import java.util.Map;

import stockholm.exceptions.StockHolmException;
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

    /**
     * Parses one line of user input.
     *
     * @param rawInput the line exactly as the user typed it
     * @return the parsed command; blank input gives {@link CommandType#NO_OP}
     * @throws StockHolmException if the command word is unknown or arguments are missing
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
            return new Command(CommandType.INV_ADD, requireName(name, "inv add NAME"));
        case "delete":
            return new Command(CommandType.INV_DELETE, requireName(name, "inv delete NAME"));
        case "list":
            return new Command(CommandType.INV_LIST);
        default:
            throw new StockHolmException("Usage: inv add|delete|list [NAME]");
        }
    }

    /** Returns {@code name} if it's non-empty. Otherwise throws with a usage hint. */
    private static String requireName(String name, String usage) throws StockHolmException {
        if (name.isEmpty()) {
            throw new StockHolmException("Missing inventory name. Usage: " + usage);
        }
        return name;
    }
}
