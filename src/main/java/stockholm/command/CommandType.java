package stockholm.command;

/**
 * Every action the Processor knows how to run.
 * The Parser turns text into one of these, so the Processor never needs to compare strings.
 *
 * <p>Each constant also records its syntax, a short description and where it may be used.
 * The Parser shows the syntax in error messages, {@code help} lists every command from these fields,
 * and the Processor checks the location. Within each location, {@code help} lists commands in the order
 * declared here.
 */
public enum CommandType {
    /** Blank input: nothing to do. Not listed in {@code help}. */
    NO_OP(null, null, Location.ANYWHERE),
    /** {@code help}: list every command and its syntax. */
    HELP("help", "show this list", Location.ANYWHERE),
    /** {@code quit} (or an alias): exit the program. */
    QUIT("quit", "exit StockHolm (also: exit, close, bye)", Location.ANYWHERE),
    /** {@code enter NAME}: enter an inventory, so item commands apply to it. */
    ENTER("enter NAME", "enter an inventory", Location.ANYWHERE),
    /** {@code stock [NAME] [--below=COUNT]}: show the stock levels of an inventory, by default the current one. */
    STOCK("stock [NAME] [--below=COUNT]", "show stock levels, of the current inventory by default",
            Location.ANYWHERE),
    /** {@code inv add NAME}: create an inventory. */
    INV_ADD("inv add NAME", "create an inventory", Location.OUTSIDE),
    /** {@code inv delete NAME}: remove an inventory. */
    INV_DELETE("inv delete NAME", "delete an inventory", Location.OUTSIDE),
    /** {@code inv list}: show all inventories. */
    INV_LIST("inv list", "list all inventories", Location.OUTSIDE),
    /** {@code back}: leave the current inventory. */
    BACK("back", "leave the current inventory", Location.INSIDE),
    /** {@code item add NAME [--type=TYPE] [--count=COUNT]}: add an item to the current inventory. */
    ITEM_ADD("item add NAME [--type=TYPE] [--count=COUNT]", "add an item, or add to an existing one",
            Location.INSIDE),
    /** {@code item list}: show the items in the current inventory. */
    ITEM_LIST("item list", "list the items", Location.INSIDE),
    /** {@code item delete INDEX}: remove the item at a one-based position in {@code item list}. */
    ITEM_DELETE("item delete INDEX", "delete the item numbered in item list", Location.INSIDE),
    /** {@code import NAME [--type=TYPE] [--count=COUNT]}: create an import order in the current inventory. */
    IMPORT("import NAME [--type=TYPE] [--count=COUNT]", "create an import order", Location.INSIDE),
    /** {@code export INDEX [--count=COUNT]}: create an export order and reduce stock in the current inventory. */
    EXPORT("export INDEX [--count=COUNT]", "create an export order, reducing stock", Location.INSIDE),
    /** {@code order list}: show all orders and their details in the current inventory. */
    ORDER_LIST("order list", "list all orders", Location.INSIDE),
    /** {@code approve INDEX}: approve an order numbered in {@code order list}. */
    APPROVE("approve INDEX", "approve the order numbered in order list", Location.INSIDE),
    /** {@code deliver INDEX}: mark an order numbered in {@code order list} as delivered. */
    DELIVER("deliver INDEX", "mark the order numbered in order list as delivered", Location.INSIDE);

    private final String usage;
    private final String description;
    private final Location location;

    CommandType(String usage, String description, Location location) {
        this.usage = usage;
        this.description = description;
        this.location = location;
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
}
