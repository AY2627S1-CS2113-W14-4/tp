package stockholm.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StockHolmException}.
 */
class StockHolmExceptionTest {
    @Test
    public void getMessage_returnsConstructorMessage() {
        assertEquals("Something went wrong", new StockHolmException("Something went wrong").getMessage());
    }
}
