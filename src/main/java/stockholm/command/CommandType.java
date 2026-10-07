package stockholm.command;

/**
 * Every action the Processor knows how to run.
 * The Parser turns text into one of these, so the Processor never needs to compare strings.
 */
public enum CommandType {
    /** Blank input: nothing to do. */
    NO_OP,
    /** {@code quit} (or an alias): exit the program. */
    QUIT,
    /** {@code inv add NAME}: create an inventory. */
    INV_ADD,
    /** {@code inv delete NAME}: remove an inventory. */
    INV_DELETE,
    /** {@code inv list}: show all inventories. */
    INV_LIST,
    /** {@code enter NAME}: enter an inventory*/
    ENTER,
    /** {@code back}: goes back to start*/
    BACK
}
