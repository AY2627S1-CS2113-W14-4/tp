package stockholm.inventory;

import java.math.BigDecimal;

/**
 * One kind of stock in an {@link Inventory}, e.g. "A4 Paper Case" of type "Stationery".
 * The count is a decimal so items measured in e.g. kilograms or litres are supported.
 */
public class Item {
    private final String name;
    /** Category of the item, or an empty string if none was given. */
    private final String type;
    private double count;

    /**
     * Creates an item.
     *
     * @param name  display name, also used to look the item up within its inventory
     * @param type  category, or an empty string for none
     * @param count how many there are; expected to be positive
     */
    public Item(String name, String type, double count) {
        this.name = name;
        this.type = type;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public double getCount() {
        return count;
    }

    /**
     * Increases the count, e.g. when the same item is added again.
     *
     * @param amount how much to add
     */
    public void addCount(double amount) {
        count += amount;
    }

    /**
     * Reduces the count after the Processor has checked that enough stock is available.
     * Decimal subtraction avoids leaving a rounding residue when exporting decimal quantities.
     *
     * @param amount how much to remove; expected to be positive and no greater than the count
     */
    public void reduceCount(double amount) {
        count = BigDecimal.valueOf(count).subtract(BigDecimal.valueOf(amount)).doubleValue();
    }

    /**
     * Formats a count without a trailing {@code .0}, e.g. {@code 2.0} becomes {@code "2"}
     * while {@code 2.5} stays {@code "2.5"}.
     *
     * @param count the number to format
     * @return the shortest plain (non-scientific) form of the number
     */
    public static String formatCount(double count) {
        return BigDecimal.valueOf(count).stripTrailingZeros().toPlainString();
    }

    /**
     * Returns the item as shown to the user, e.g. {@code A4 Paper Case (Stationery) x2}.
     * The type part is left out if the item has no type.
     */
    @Override
    public String toString() {
        String typePart = type.isEmpty() ? "" : " (" + type + ")";
        return name + typePart + " x" + formatCount(count);
    }
}
