package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Result} record's generated accessors and equality.
 */
class ResultTest {
    @Test
    public void accessors_returnConstructorValues() {
        Result result = new Result("hello", true);
        assertEquals("hello", result.message());
        assertTrue(result.isExit());
    }

    @Test
    public void equals_sameValues_areEqual() {
        assertEquals(new Result("msg", false), new Result("msg", false));
    }

    @Test
    public void equals_differentValues_areNotEqual() {
        assertNotEquals(new Result("msg", false), new Result("msg", true));
        assertNotEquals(new Result("a", false), new Result("b", false));
        assertFalse(new Result("", false).isExit());
    }
}
