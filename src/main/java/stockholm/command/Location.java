package stockholm.command;

/**
 * Where a command may be used: inside an inventory (after {@code enter}), outside all inventories, or anywhere.
 * The Processor rejects a command used in the wrong place before running it.
 */
public enum Location {
    /** Allowed both inside and outside an inventory, e.g. {@code help} and {@code stock}. */
    ANYWHERE("Anywhere:"),
    /** Allowed only outside all inventories, e.g. {@code inv add}. */
    OUTSIDE("Outside an inventory:"),
    /** Allowed only inside an inventory (after {@code enter}), e.g. {@code item add}. */
    INSIDE("Inside an inventory:");

    private final String helpHeading;

    Location(String helpHeading) {
        this.helpHeading = helpHeading;
    }

    /**
     * Returns the heading that groups this location's commands in {@code help}.
     *
     * @return the heading, e.g. {@code "Anywhere:"}
     */
    public String helpHeading() {
        return helpHeading;
    }
}
