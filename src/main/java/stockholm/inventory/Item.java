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
    /** Minimum stock level the user wants to keep; {@code 0} means none is set. */
    private double minCount;

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

    /** Returns a copy with edited details, keeping its stock count and minimum. */
    public Item withDetails(String newName, String newType) {
        Item edited = new Item(newName, newType, count);
        edited.setMinCount(minCount);
        return edited;
    }

    /**
     * Returns the minimum stock level set with {@code item min}, or {@code 0} if none is set.
     *
     * @return the minimum, never negative
     */
    public double getMinCount() {
        return minCount;
    }

    /**
     * Sets the minimum stock level below which this item counts as low on stock.
     *
     * @param minCount a finite, non-negative number; {@code 0} clears the minimum
     */
    public void setMinCount(double minCount) {
        assert Double.isFinite(minCount) && minCount >= 0 : "A minimum must be finite and non-negative: " + minCount;
        this.minCount = minCount;
    }

    /**
     * Returns whether a minimum is set and the count has fallen below it.
     * An item exactly at its minimum is not low.
     *
     * @return {@code true} if the item should be restocked
     */
    public boolean isLowOnStock() {
        return minCount > 0 && count < minCount;
    }

    /**
     * Returns how many units to order to bring the count back up to the minimum.
     *
     * @return the shortfall, or {@code 0} if the item is not low on stock
     */
    public double getShortfall() {
        if (!isLowOnStock()) {
            return 0;
        }
        return BigDecimal.valueOf(minCount).subtract(BigDecimal.valueOf(count)).doubleValue();
    }

    /**
     * Increases the count, e.g. when the same item is added again.
     * Decimal addition avoids a rounding residue, so {@code 0.1 + 0.2} gives {@code 0.3}.
     *
     * @param amount how much to add
     */
    public void addCount(double amount) {
        count = BigDecimal.valueOf(count).add(BigDecimal.valueOf(amount)).doubleValue();
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
