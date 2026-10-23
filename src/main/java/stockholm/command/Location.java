package stockholm.command;

/**
 * Where a command may be used: inside an inventory (after {@code enter}), outside all inventories, or anywhere.
 * The Processor rejects a command used in the wrong place before running it.
 */
public enum Location {
    ANYWHERE("Anywhere:"),
    OUTSIDE("Outside an inventory:"),
    INSIDE("Inside an inventory:");

    private final String helpHeading;

    Location(String helpHeading) {
        this.helpHeading = helpHeading;
    }

    /** Returns the heading that groups this location's commands in {@code help}. */
    public String helpHeading() {
        return helpHeading;
    }
}
