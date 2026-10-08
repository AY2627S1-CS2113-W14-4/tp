package stockholm.command;

/**
 * Names of the arguments a {@link Command} can carry.
 * Using an enum instead of plain strings turns a mistyped key into a compile error.
 * Add a new constant here together with the command that first needs it.
 */
public enum ArgKey {
    /** Name of the target inventory, e.g. {@code Shop} in {@code inv add Shop}. */
    INV_NAME,
    /** Name of an item, e.g. {@code A4 Paper Case} in {@code item add "A4 Paper Case"}. */
    ITEM_NAME,
    /** Optional item category from {@code --type=TYPE}. */
    ITEM_TYPE,
    /** Optional item count from {@code --count=COUNT}: a positive decimal number, e.g. {@code 2} or {@code 2.5}. */
    ITEM_COUNT,
    /** One-based position of an item in {@code item list}, e.g. {@code 3} in {@code item delete 3}. */
    ITEM_INDEX
}
