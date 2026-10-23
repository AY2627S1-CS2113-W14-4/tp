package stockholm.processor;

import stockholm.command.ArgKey;
import stockholm.command.Command;

/**
 * Reads the optional item arguments of a command, filling in their defaults.
 * Shared by {@code item add} and {@code import}, which take the same {@code --type} and {@code --count} options.
 */
final class ItemArgs {
    /** Default count for item data when no {@code --count} is given. */
    private static final double DEFAULT_ITEM_COUNT = 1;

    private ItemArgs() {
    }

    /** Returns the {@code --count} argument as a number, or the default count if none was given. */
    static double countOrDefault(Command command) {
        return command.hasArg(ArgKey.ITEM_COUNT)
                ? Double.parseDouble(command.getArg(ArgKey.ITEM_COUNT))
                : DEFAULT_ITEM_COUNT;
    }

    /** Returns the {@code --type} argument, or an empty type if none was given. */
    static String typeOrEmpty(Command command) {
        return command.hasArg(ArgKey.ITEM_TYPE) ? command.getArg(ArgKey.ITEM_TYPE) : "";
    }
}
