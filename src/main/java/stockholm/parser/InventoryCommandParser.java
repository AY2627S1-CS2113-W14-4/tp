package stockholm.parser;

import static stockholm.parser.ArgumentParser.requireArg;
import static stockholm.parser.ArgumentParser.splitFirstWord;

import java.util.Map;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/** Parses commands that manage inventories: {@code inv add|rename|delete|list} and {@code enter}. */
final class InventoryCommandParser {
    private InventoryCommandParser() {
    }

    /** Parses the part after {@code inv}, e.g. {@code "add Shop"} or {@code "list"}. */
    static Command parseInvCommand(String rest) throws StockHolmException {
        String[] parts = splitFirstWord(rest);
        String subcommand = parts[0];
        String name = parts[1];

        switch (subcommand) {
        case "add":
            return new Command(CommandType.INV_ADD,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", CommandType.INV_ADD.usage())));
        case "rename":
            return parseRename(name);
        case "delete":
            return new Command(CommandType.INV_DELETE,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", CommandType.INV_DELETE.usage())));
        case "list":
            return new Command(CommandType.INV_LIST);
        default:
            throw new StockHolmException("Usage: inv add|rename|delete|list ...");
        }
    }

    /** Parses two names; quote the old name when it contains spaces. The new name uses the remaining text. */
    private static Command parseRename(String arguments) throws StockHolmException {
        String usage = CommandType.INV_RENAME.usage();
        String oldName;
        String newName;
        if (arguments.startsWith("\"")) {
            int closingQuote = arguments.indexOf('"', 1);
            if (closingQuote < 0) {
                throw new StockHolmException("Missing closing quote in old inventory name. Usage: " + usage);
            }
            oldName = arguments.substring(1, closingQuote);
            newName = arguments.substring(closingQuote + 1).trim();
        } else {
            String[] names = splitFirstWord(arguments);
            oldName = names[0];
            newName = names[1];
        }
        requireArg(oldName.trim(), "old inventory name", usage);
        requireArg(newName, "new inventory name", usage);
        // Match inv add: quotes typed around a new name are part of that name, not delimiters.
        return new Command(CommandType.INV_RENAME, Map.of(ArgKey.INV_NAME, oldName,
                ArgKey.INV_NEW_NAME, newName));
    }

    /** Parses the part after {@code enter}: the name of the inventory to enter. */
    static Command parseEnter(String rest) throws StockHolmException {
        return new Command(CommandType.ENTER,
                Map.of(ArgKey.INV_NAME, requireArg(rest, "inventory name", CommandType.ENTER.usage())));
    }
}
