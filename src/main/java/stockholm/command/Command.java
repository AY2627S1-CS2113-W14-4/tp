package stockholm.command;

import java.util.Map;

/**
 * The output of the Parser and the input of the Processor: a fully parsed user command.
 * It is an op code ({@link CommandType}) plus named arguments, so new commands can carry
 * any arguments they need without adding fields to this record.
 *
 * <p>Arguments are strings: the Parser checks their syntax, and the Processor converts them
 * (e.g. to numbers) or uses them to look up objects such as an inventory by name.
 *
 * <p>It is immutable: it is a {@code record}, and the argument map is copied on construction.
 *
 * @param type which action to run
 * @param args the command's arguments; empty if it takes none
 */
public record Command(CommandType type, Map<ArgKey, String> args) {
    /**
     * Compact constructor: stores an unmodifiable copy of {@code args}, so changing the
     * caller's map afterwards cannot change this command. Null keys or values are rejected.
     */
    public Command {
        args = Map.copyOf(args);
    }

    /**
     * Convenience constructor for commands that take no arguments (e.g. {@code quit}).
     *
     * @param type which action to run
     */
    public Command(CommandType type) {
        this(type, Map.of());
    }

    /**
     * Returns a required argument.
     *
     * @param key which argument to get
     * @return the argument's value
     * @throws IllegalStateException if the argument is missing, which means the Parser has a bug
     */
    public String getArg(ArgKey key) {
        String value = args.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing argument " + key + " for command " + type);
        }
        return value;
    }

    /**
     * Checks whether an argument is present; useful for optional arguments.
     *
     * @param key which argument to check
     * @return {@code true} if the command has that argument
     */
    public boolean hasArg(ArgKey key) {
        return args.containsKey(key);
    }
}
