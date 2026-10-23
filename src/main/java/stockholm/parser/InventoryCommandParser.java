package stockholm.parser;

import static stockholm.parser.ArgumentParser.requireArg;
import static stockholm.parser.ArgumentParser.splitFirstWord;

import java.util.Map;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/** Parses commands that manage inventories: {@code inv add|delete|list} and {@code enter}. */
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
        case "delete":
            return new Command(CommandType.INV_DELETE,
                    Map.of(ArgKey.INV_NAME, requireArg(name, "inventory name", CommandType.INV_DELETE.usage())));
        case "list":
            return new Command(CommandType.INV_LIST);
        default:
            throw new StockHolmException("Usage: inv add|delete|list [NAME]");
        }
    }

    /** Parses the part after {@code enter}: the name of the inventory to enter. */
    static Command parseEnter(String rest) throws StockHolmException {
        return new Command(CommandType.ENTER,
                Map.of(ArgKey.INV_NAME, requireArg(rest, "inventory name", CommandType.ENTER.usage())));
    }
}
