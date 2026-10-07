package stockholm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the printing methods of {@link Ui}.
 * {@link Ui#readInputFancy()} is not tested here because it reads from a static Scanner on
 * System.in (see {@link InputReader}), which cannot be reliably replaced once the class is loaded.
 */
class UiTest {
    private static final String NL = System.lineSeparator();

    private final PrintStream originalOut = System.out;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @BeforeEach
    public void redirectOut() {
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void restoreOut() {
        System.setOut(originalOut);
    }

    @Test
    public void printStarter_printsWelcomeMessage() {
        Ui.printStarter();
        assertEquals("Hello, Welcome to StockHolm, your inventory manager program" + NL,
                outContent.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void printEnding_printsGoodbyeMessage() {
        Ui.printEnding();
        assertEquals("See you next time" + NL, outContent.toString(StandardCharsets.UTF_8));
    }
}
