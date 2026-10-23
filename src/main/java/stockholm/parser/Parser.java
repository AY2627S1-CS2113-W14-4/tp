package stockholm.parser;

import java.util.Map;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Turns a raw input line into a {@link Command}.
 * It only checks syntax (e.g. a missing argument or an unknown command word).
 * It never looks at inventory data. Checking whether an inventory exists is the Processor's job.
 *
 * <p>This class reads the command word and hands the rest to a parser for that area:
 * {@link InventoryCommandParser}, {@link ItemCommandParser} or {@link OrderCommandParser}.
 * They share {@link ArgumentParser} for names, options and numbers.
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
     * @throws StockHolmException if the command word is unknown or arguments are missing or malformed
     */
    public static Command parseCommand(String rawInput) throws StockHolmException {
        String[] parts = ArgumentParser.splitFirstWord(rawInput);
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
            return InventoryCommandParser.parseInvCommand(rest);
        case "enter":
            return InventoryCommandParser.parseEnter(rest);
        case "back":
            return new Command(CommandType.BACK);
        case "item":
            return ItemCommandParser.parseItemCommand(rest);
        case "order":
            return OrderCommandParser.parseOrderCommand(rest);
        case "import":
            return OrderCommandParser.parseImport(rest);
        case "export":
            return OrderCommandParser.parseExport(rest);
        case "approve":
            return OrderCommandParser.parseOrderIndex(rest, CommandType.APPROVE);
        case "reject":
            return OrderCommandParser.parseReject(rest);
        case "deliver":
            return OrderCommandParser.parseOrderIndex(rest, CommandType.DELIVER);
        case "stock":
            return ItemCommandParser.parseStock(rest);
        case "find":
            return ItemCommandParser.parseFind(rest);
        default:
            throw new StockHolmException("Unknown command: " + commandWord);
        }
    }

    /** Parses {@code help}, which takes no arguments. */
    private static Command parseHelp(String rest) throws StockHolmException {
        if (!rest.isEmpty()) {
            throw new StockHolmException("Usage: " + CommandType.HELP.usage());
        }
        return new Command(CommandType.HELP);
    }
}
