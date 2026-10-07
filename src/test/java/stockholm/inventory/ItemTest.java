package stockholm.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Item}.
 */
class ItemTest {
    @Test
    public void getters_returnConstructorValues() {
        Item item = new Item("A4 Paper Case", "Stationery", 2);
        assertEquals("A4 Paper Case", item.getName());
        assertEquals("Stationery", item.getType());
        assertEquals(2.0, item.getCount());
    }

    @Test
    public void addCount_positiveAmount_increasesCount() {
        Item item = new Item("Rice", "Food", 2.5);
        item.addCount(1.5);
        assertEquals(4.0, item.getCount());
    }

    @Test
    public void formatCount_wholeAndDecimalNumbers_dropsTrailingZeros() {
        assertEquals("2", Item.formatCount(2.0));
        assertEquals("2.5", Item.formatCount(2.5));
        assertEquals("100", Item.formatCount(100.0));
        assertEquals("0.25", Item.formatCount(0.25));
    }

    @Test
    public void toString_withType_includesTypeInBrackets() {
        assertEquals("A4 Paper Case (Stationery) x2", new Item("A4 Paper Case", "Stationery", 2).toString());
    }

    @Test
    public void toString_withoutType_omitsBrackets() {
        assertEquals("Pen x1.5", new Item("Pen", "", 1.5).toString());
    }
}
