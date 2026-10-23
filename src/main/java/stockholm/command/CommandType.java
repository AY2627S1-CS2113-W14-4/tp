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
    HELP("help", "show this list", Location.ANYWHERE),
    QUIT("quit", "exit StockHolm (also: exit, close, bye)", Location.ANYWHERE),
    ENTER("enter NAME", "enter an inventory", Location.ANYWHERE),
    STOCK("stock [NAME] [--below=COUNT]", "show stock levels, of the current inventory by default",
            Location.ANYWHERE),
    INV_ADD("inv add NAME", "create an inventory", Location.OUTSIDE),
    INV_DELETE("inv delete NAME", "delete an inventory", Location.OUTSIDE),
    INV_LIST("inv list", "list all inventories", Location.OUTSIDE),
    BACK("back", "leave the current inventory", Location.INSIDE),
    ITEM_ADD("item add NAME [--type=TYPE] [--count=COUNT]", "add an item, or add to an existing one",
            Location.INSIDE),
    ITEM_LIST("item list", "list the items", Location.INSIDE),
    ITEM_DELETE("item delete INDEX", "delete the item numbered in item list", Location.INSIDE),
    IMPORT("import NAME [--type=TYPE] [--count=COUNT]", "create an import order", Location.INSIDE),
    EXPORT("export INDEX [--count=COUNT]", "create an export order, reducing stock", Location.INSIDE),
    ORDER_LIST("order list", "list all orders", Location.INSIDE),
    APPROVE("approve INDEX", "approve the order numbered in order list", Location.INSIDE),
    DELIVER("deliver INDEX", "mark the order numbered in order list as delivered", Location.INSIDE);

    private final String usage;
    private final String description;
    private final Location location;

    CommandType(String usage, String description, Location location) {
        this.usage = usage;
        this.description = description;
        this.location = location;
    }

    /** Returns the syntax, e.g. {@code "inv add NAME"}; CAPS are values to fill in, {@code [ ]} is optional. */
    public String usage() {
        return usage;
    }

    /** Returns what the command does, as shown in {@code help}. */
    public String description() {
        return description;
    }

    /** Returns where the command may be used. */
    public Location location() {
        return location;
    }
}
