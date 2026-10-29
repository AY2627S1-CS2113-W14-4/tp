package stockholm.command;

/**
 * Names of the arguments a {@link Command} can carry.
 * Using an enum instead of plain strings turns a mistyped key into a compile error.
 * Add a new constant here together with the command that first needs it.
 */
public enum ArgKey {
    /** Name of the target inventory, e.g. {@code Shop} in {@code inv add Shop}. */
    INV_NAME,
    /** New name requested by {@code inv rename OLD_NAME NEW_NAME}. */
    INV_NEW_NAME,
    /** Optional source inventory from {@code transfer --from=NAME}. */
    INV_FROM,
    /** Optional destination inventory from {@code transfer --to=NAME}. */
    INV_TO,
    /** Name of an item, e.g. {@code A4 Paper Case} in {@code item add "A4 Paper Case"}. */
    ITEM_NAME,
    /** Optional item category from {@code --type=TYPE}. */
    ITEM_TYPE,
    /** Optional item count from {@code --count=COUNT}: a positive decimal number, e.g. {@code 2} or {@code 2.5}. */
    ITEM_COUNT,
    /** One-based position of an item in {@code item list}, e.g. {@code 3} in {@code item delete 3}. */
    ITEM_INDEX,
    /** One-based position of an order in {@code order list}, e.g. {@code 2} in {@code approve 2}. */
    ORDER_INDEX,
    /** Optional low-stock threshold from {@code stock --below=COUNT}: a positive decimal number. */
    STOCK_BELOW,
    /** Text to look for in item names, from {@code find KEYWORD}; matched case-insensitively. */
    FIND_KEYWORD,
    /** Reason for rejecting an order, from {@code reject INDEX REASON}. */
    ORDER_REASON,
    /** Minimum stock level from {@code item min INDEX COUNT}: {@code 0} or a positive decimal number. */
    ITEM_MIN
}
