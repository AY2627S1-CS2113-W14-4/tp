package stockholm.command;

/**
 * Every action the Processor knows how to run.
 * The Parser turns text into one of these, so the Processor never needs to compare strings.
 *
 * <p>Each constant also records its syntax, a short description, where it may be used, and which saved data it
 * changes. The Parser shows the syntax in error messages, {@code help} lists every command from these fields,
 * the Processor checks the location, and Storage saves what the command changes.
 * Within each location, {@code help} lists commands in the order declared here.
 */
public enum CommandType {
    /** Blank input: nothing to do. Not listed in {@code help}. */
    NO_OP(null, null, Location.ANYWHERE, Changes.NONE),
    /** {@code help}: list every command and its syntax. */
    HELP("help", "show this list", Location.ANYWHERE, Changes.NONE),
    /** {@code quit} (or an alias): exit the program. */
    QUIT("quit", "exit StockHolm (also: exit, close, bye)", Location.ANYWHERE, Changes.NONE),
    /** {@code enter NAME}: enter an inventory, so item commands apply to it. */
    ENTER("enter NAME", "enter an inventory", Location.ANYWHERE, Changes.NONE),
    /** {@code stock [NAME] [--below=COUNT]}: show the stock levels of an inventory, by default the current one. */
    STOCK("stock [NAME] [--below=COUNT]", "show stock levels, of the current inventory by default",
            Location.ANYWHERE, Changes.NONE),
    /**
     * {@code find [KEYWORD] [--type=TYPE]}: search items by name and/or type,
     * in the current inventory or, outside one, in every inventory.
     */
    FIND("find [KEYWORD] [--type=TYPE]", "search items by name and/or type (in every inventory when outside one)",
            Location.ANYWHERE, Changes.NONE),
    /** {@code restock [NAME]}: list items below their minimum, of the current inventory by default. */
    RESTOCK("restock [NAME]", "list items below their minimum stock, of the current inventory by default",
            Location.ANYWHERE, Changes.NONE),
    /** {@code inv add NAME}: create an inventory. */
    INV_ADD("inv add NAME", "create an inventory", Location.OUTSIDE, Changes.INVENTORIES),
    /** {@code inv delete NAME}: remove an inventory, together with its orders. */
    INV_DELETE("inv delete NAME", "delete an inventory", Location.OUTSIDE, Changes.INVENTORIES_AND_ORDERS),
    /** {@code inv list}: show all inventories. */
    INV_LIST("inv list", "list all inventories", Location.OUTSIDE, Changes.NONE),
    /** {@code back}: leave the current inventory. */
    BACK("back", "leave the current inventory", Location.INSIDE, Changes.NONE),
    /** {@code item add NAME [--type=TYPE] [--count=COUNT]}: add an item to the current inventory. */
    ITEM_ADD("item add NAME [--type=TYPE] [--count=COUNT]", "add an item, or add to an existing one",
            Location.INSIDE, Changes.INVENTORIES),
    /** {@code item list}: show the items in the current inventory. */
    ITEM_LIST("item list", "list the items", Location.INSIDE, Changes.NONE),
    /** {@code item delete INDEX}: remove the item at a one-based position in {@code item list}. */
    ITEM_DELETE("item delete INDEX", "delete the item numbered in item list", Location.INSIDE, Changes.INVENTORIES),
    /** {@code item import CSV_PATH}: validate and add items from a CSV file. */
    ITEM_IMPORT("item import CSV_PATH", "import items from a CSV file", Location.INSIDE, Changes.INVENTORIES),
    /** {@code item min INDEX COUNT}: set (or with {@code 0}, clear) the minimum stock level of an item. */
    ITEM_MIN("item min INDEX COUNT", "alert when the item falls below COUNT (0 clears it)", Location.INSIDE,
            Changes.INVENTORIES),
    /** {@code import NAME [--type=TYPE] [--count=COUNT]}: create an import order in the current inventory. */
    IMPORT("import NAME [--type=TYPE] [--count=COUNT]", "create an import order", Location.INSIDE, Changes.ORDERS),
    /** {@code export INDEX [--count=COUNT]}: create an export order and reduce stock in the current inventory. */
    EXPORT("export INDEX [--count=COUNT]", "create an export order, reducing stock", Location.INSIDE,
            Changes.INVENTORIES_AND_ORDERS),
    /** {@code order list}: show all orders and their details in the current inventory. */
    ORDER_LIST("order list", "list all orders", Location.INSIDE, Changes.NONE),
    /** {@code approve INDEX}: approve an order numbered in {@code order list}. */
    APPROVE("approve INDEX", "approve the order numbered in order list", Location.INSIDE, Changes.ORDERS),
    /** {@code reject INDEX REASON}: reject an order numbered in {@code order list} with a reason. */
    REJECT("reject INDEX REASON", "reject the order numbered in order list with a reason", Location.INSIDE,
            Changes.INVENTORIES_AND_ORDERS),
    /** {@code cancel INDEX}: cancel an order numbered in {@code order list}. */
    CANCEL("cancel INDEX", "cancel the order numbered in order list", Location.INSIDE,
            Changes.INVENTORIES_AND_ORDERS),
    /** {@code deliver INDEX}: mark an order delivered; an import order's item is added to stock. */
    DELIVER("deliver INDEX", "mark the order numbered in order list as delivered", Location.INSIDE,
            Changes.INVENTORIES_AND_ORDERS);

    private final String usage;
    private final String description;
    private final Location location;
    private final Changes changes;

    CommandType(String usage, String description, Location location, Changes changes) {
        this.usage = usage;
        this.description = description;
        this.location = location;
        this.changes = changes;
    }

    /**
     * Returns the syntax, e.g. {@code "inv add NAME"}; CAPS are values to fill in, {@code [ ]} is optional.
     *
     * @return the syntax, or {@code null} for {@link #NO_OP}
     */
    public String usage() {
        return usage;
    }

    /**
     * Returns what the command does, as shown in {@code help}.
     *
     * @return the description, or {@code null} for {@link #NO_OP}
     */
    public String description() {
        return description;
    }

    /**
     * Returns where the command may be used.
     *
     * @return the location the Processor checks before running the command
     */
    public Location location() {
        return location;
    }

    /**
     * Returns which saved data the command changes when it succeeds.
     *
     * @return what Storage must save after the command
     */
    public Changes changes() {
        return changes;
    }
}
