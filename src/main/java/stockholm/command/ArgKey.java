package stockholm.command;

/**
 * Names of the arguments a {@link Command} can carry.
 * Using an enum instead of plain strings turns a mistyped key into a compile error.
 * Add a new constant here together with the command that first needs it.
 */
public enum ArgKey {
    /** Name of the target inventory, e.g. {@code Shop} in {@code inv add Shop}. */
    INV_NAME
}
