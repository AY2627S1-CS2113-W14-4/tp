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
    /** {@code enter NAME}: enter an inventory, so item commands apply to it. */
    ENTER,
    /** {@code back}: leave the current inventory. */
    BACK,
    /** {@code item add NAME [--type=TYPE] [--count=COUNT]}: add an item to the current inventory. */
    ITEM_ADD,
    /** {@code item list}: show the items in the current inventory. */
    ITEM_LIST,
    /** {@code item delete INDEX}: remove the item at a one-based position in {@code item list}. */
    ITEM_DELETE,
    /** {@code import NAME [--type=TYPE] [--count=COUNT]}: create an import order in the current inventory. */
    IMPORT
}
