package stockholm.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import stockholm.processor.Result;

/**
 * Tests for {@link Printer}.
 * System.out and System.err are redirected into in-memory buffers before each test
 * so the printed text can be checked, then restored afterwards.
 */
class PrinterTest {
    private static final String NL = System.lineSeparator();

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @BeforeEach
    public void redirectStreams() {
        System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String out() {
        return outContent.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errContent.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void printResult_nonEmptyMessage_printsIndented() {
        Printer.printResult(new Result("Added inventory: Shop", false));
        assertEquals("    Added inventory: Shop" + NL, out());
    }

    @Test
    public void printResult_emptyMessage_printsNothing() {
        Printer.printResult(new Result("", true));
        assertEquals("", out());
    }

    @Test
    public void printPrompt_noArgs_printsPromptWithoutNewline() {
        Printer.printPrompt();
        assertEquals("❯ ", out());
    }

    @Test
    public void printPrompt_withInventoryName_printsNamedPrompt() {
        Printer.printPrompt("Shop");
        assertEquals("Shop❯ " + NL, out());
    }

    @Test
    public void printIndent_singleLine_addsFourSpaces() {
        Printer.printIndent("hello");
        assertEquals("    hello" + NL, out());
    }

    @Test
    public void printIndent_multiLine_indentsEveryLine() {
        Printer.printIndent("a\nb\nc");
        assertEquals("    a\n    b\n    c" + NL, out());
    }

    @Test
    public void printIndentedError_multiLine_writesToStdErr() {
        Printer.printIndentedError("bad\ninput");
        assertEquals("    bad\n    input" + NL, err());
        assertEquals("", out());
    }

    @Test
    public void printBar_printsBarOfTerminalWidth() {
        Printer.printBar();
        // The width depends on the COLUMNS environment variable, with a fallback of 100.
        int expectedWidth = expectedBarWidth();
        assertEquals("─".repeat(expectedWidth) + NL, out());
    }

    /** Mirrors Printer's width logic so the test works whether or not COLUMNS is set. */
    private static int expectedBarWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns == null) {
            return 100;
        }
        try {
            return Integer.parseInt(columns.trim());
        } catch (NumberFormatException e) {
            return 100;
        }
    }
}
