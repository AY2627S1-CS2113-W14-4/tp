package stockholm.command;

/**
 * Which saved data a command changes when it succeeds. Storage uses this to decide which files to save
 * after each command, so a command that changes nothing never writes a file.
 */
public enum Changes {
    /** Changes nothing that is saved, e.g. {@code item list} or {@code enter}. */
    NONE(false, false),
    /** Changes inventories or their items, e.g. {@code item add}. */
    INVENTORIES(true, false),
    /** Changes orders only, e.g. {@code approve}. */
    ORDERS(false, true),
    /** Changes both, e.g. {@code export} reduces stock and creates an order. */
    INVENTORIES_AND_ORDERS(true, true);

    private final boolean includesInventories;
    private final boolean includesOrders;

    Changes(boolean includesInventories, boolean includesOrders) {
        this.includesInventories = includesInventories;
        this.includesOrders = includesOrders;
    }

    /**
     * Returns whether inventories or their items may differ after the command.
     *
     * @return {@code true} if the inventory file must be saved
     */
    public boolean includesInventories() {
        return includesInventories;
    }

    /**
     * Returns whether orders may differ after the command.
     *
     * @return {@code true} if the order file must be saved
     */
    public boolean includesOrders() {
        return includesOrders;
    }
}
