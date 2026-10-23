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
    /** {@code help}: list every command and its syntax. */
    HELP,
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
    /** {@code order list}: show all orders and their details in the current inventory. */
    ORDER_LIST,
    /** {@code approve INDEX}: approve an order numbered in {@code order list}. */
    APPROVE,
    /** {@code deliver INDEX}: mark an order numbered in {@code order list} as delivered. */
    DELIVER,
    /** {@code import NAME [--type=TYPE] [--count=COUNT]}: create an import order in the current inventory. */
    IMPORT,
    /** {@code export INDEX [--count=COUNT]}: create an export order and reduce stock in the current inventory. */
    EXPORT,
    /** {@code stock [NAME] [--below=COUNT]}: show the stock levels of an inventory, by default the current one. */
    STOCK,
    /**
     * {@code find [KEYWORD] [--type=TYPE]}: search items by name and/or type,
     * in the current inventory or, outside one, in every inventory.
     */
    FIND,
    /** {@code item min INDEX COUNT}: set (or with {@code 0}, clear) the minimum stock level of an item. */
    ITEM_MIN,
    /** {@code restock [NAME]}: list items below their minimum, of the current inventory by default. */
    RESTOCK
}
