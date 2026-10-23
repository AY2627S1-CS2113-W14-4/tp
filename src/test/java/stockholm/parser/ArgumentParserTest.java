package stockholm.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;

/**
 * Tests for {@link ArgumentParser}, the helpers shared by every command parser.
 * Whole commands are tested through {@link Parser#parseCommand(String)} in {@code ParserTest}.
 */
class ArgumentParserTest {
    private static final String USAGE = "cmd NAME [--a=A] [--b=B]";

    // ---------- splitFirstWord ----------

    @Test
    public void splitFirstWord_anyWhitespace_splitsOnceAndTrims() {
        assertArrayEquals(new String[]{"item", "add  Pen"}, ArgumentParser.splitFirstWord("  item \t add  Pen  "));
    }

    @Test
    public void splitFirstWord_oneWordOrBlank_givesEmptyRest() {
        assertArrayEquals(new String[]{"list", ""}, ArgumentParser.splitFirstWord("list"));
        assertArrayEquals(new String[]{"", ""}, ArgumentParser.splitFirstWord("   "));
    }

    // ---------- splitNameAndOptions ----------

    @Test
    public void splitNameAndOptions_quotedName_keepsSpacesAndRemovesQuotes() throws StockHolmException {
        assertArrayEquals(new String[]{"A4 Paper Case", " --a=1"},
                ArgumentParser.splitNameAndOptions("\"A4 Paper Case\" --a=1", "item name", USAGE));
    }

    @Test
    public void splitNameAndOptions_unquotedName_runsUntilFirstOption() throws StockHolmException {
        assertArrayEquals(new String[]{"Blue Pen", "--a=1"},
                ArgumentParser.splitNameAndOptions("Blue Pen --a=1", "item name", USAGE));
        assertArrayEquals(new String[]{"Pen", ""}, ArgumentParser.splitNameAndOptions("Pen", "item name", USAGE));
    }

    @Test
    public void splitNameAndOptions_unclosedQuote_throwsWithLabelAndUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.splitNameAndOptions("\"A4 Paper", "item name", USAGE));
        assertEquals("Missing closing quote in item name. Usage: " + USAGE, e.getMessage());
    }

    // ---------- parseOptions ----------

    @Test
    public void parseOptions_empty_returnsEmptyMap() throws StockHolmException {
        assertTrue(ArgumentParser.parseOptions("", USAGE, Set.of("a")).isEmpty());
    }

    @Test
    public void parseOptions_quotedAndUnquotedValues_keptInOrderGiven() throws StockHolmException {
        Map<String, String> options = ArgumentParser.parseOptions(" --b=\"two words\" --a=1", USAGE, Set.of("a", "b"));
        assertEquals(Map.of("a", "1", "b", "two words"), options);
        assertEquals(List.of("b", "a"), List.copyOf(options.keySet()));
    }

    @Test
    public void parseOptions_notAnOption_throwsInvalidOption() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.parseOptions("--a=1 extra", USAGE, Set.of("a")));
        assertEquals("Invalid option: extra. Usage: " + USAGE, e.getMessage());
    }

    @Test
    public void parseOptions_repeatedOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.parseOptions("--a=1 --a=2", USAGE, Set.of("a")));
        assertEquals("Option given more than once: --a", e.getMessage());
    }

    @Test
    public void parseOptions_unknownOption_throwsFirstUnknownInOrderGiven() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.parseOptions("--a=1 --y=2 --x=3", USAGE, Set.of("a")));
        assertEquals("Unknown option --y. Usage: " + USAGE, e.getMessage());
    }

    @Test
    public void parseOptions_malformedBeforeUnknown_reportsMalformedFirst() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.parseOptions("--x=1 oops", USAGE, Set.of("a")));
        assertEquals("Invalid option: oops. Usage: " + USAGE, e.getMessage());
    }

    // ---------- requireArg ----------

    @Test
    public void requireArg_present_returnsValue() throws StockHolmException {
        assertEquals("Shop", ArgumentParser.requireArg("Shop", "inventory name", USAGE));
    }

    @Test
    public void requireArg_empty_throwsWithLabelAndUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.requireArg("", "inventory name", USAGE));
        assertEquals("Missing inventory name. Usage: " + USAGE, e.getMessage());
    }

    // ---------- requireIndex ----------

    @Test
    public void requireIndex_positiveWholeNumber_returnsValue() throws StockHolmException {
        assertEquals("1", ArgumentParser.requireIndex("1", "item number", USAGE));
        assertEquals(String.valueOf(Integer.MAX_VALUE),
                ArgumentParser.requireIndex(String.valueOf(Integer.MAX_VALUE), "item number", USAGE));
    }

    @Test
    public void requireIndex_missing_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> ArgumentParser.requireIndex("", "order number", USAGE));
        assertEquals("Missing order number. Usage: " + USAGE, e.getMessage());
    }

    @Test
    public void requireIndex_notPositiveWholeInt_throwsCapitalisedLabel() {
        for (String value : new String[]{"0", "-1", "1.5", "abc", "2147483648"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> ArgumentParser.requireIndex(value, "order number", USAGE), value);
            assertEquals("Order number must be a positive whole number: " + value, e.getMessage());
        }
    }

    // ---------- requirePositiveDecimal ----------

    @Test
    public void requirePositiveDecimal_wholeOrDecimal_returnsValue() throws StockHolmException {
        assertEquals("2", ArgumentParser.requirePositiveDecimal("2", "Count"));
        assertEquals("2.5", ArgumentParser.requirePositiveDecimal("2.5", "Count"));
    }

    @Test
    public void requirePositiveDecimal_invalid_throwsWithLabel() {
        String tooLarge = "9".repeat(400);
        for (String value : new String[]{"0", "0.0", "-1", "+1", "1e3", ".5", "2.", "abc", tooLarge}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> ArgumentParser.requirePositiveDecimal(value, "Threshold"), value);
            assertEquals("Threshold must be a positive number, e.g. 2 or 2.5: " + value, e.getMessage());
        }
    }
}
