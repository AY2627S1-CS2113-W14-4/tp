package stockholm.command;

/**
 * The output of the Parser and the input of the Processor: a fully parsed user command.
 * It is immutable because it is a {@code record}. Java generates the constructor, the getters
 * ({@code type()}, {@code inventoryName()}), {@code equals} and {@code toString}.
 *
 * <p>Add more fields here (e.g. {@code itemName}, {@code count}) when item commands are added.
 *
 * @param type          which action to run
 * @param inventoryName target inventory name, or {@code null} if the command doesn't need one
 */
public record Command(CommandType type, String inventoryName) {
    /**
     * Convenience constructor for commands that take no arguments (e.g. {@code quit}).
     *
     * @param type which action to run
     */
    public Command(CommandType type) {
        this(type, null);
    }
}
