package stockholm.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Tests for {@link Parser#parseCommand(String)}.
 * The private helpers (splitFirstWord, parseInvCommand, parseOptions, requireArg, ...) are covered indirectly
 * through the public method, which is the usual way to test private code.
 */
class ParserTest {
    /** Builds the command expected for an {@code inv} subcommand that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }

    @Test
    public void parseCommand_emptyInput_returnsNoOp() throws StockHolmException {
        assertEquals(new Command(CommandType.NO_OP), Parser.parseCommand(""));
    }

    @Test
    public void parseCommand_whitespaceOnly_returnsNoOp() throws StockHolmException {
        assertEquals(new Command(CommandType.NO_OP), Parser.parseCommand("   \t  "));
    }

    @Test
    public void parseCommand_quit_returnsQuit() throws StockHolmException {
        assertEquals(new Command(CommandType.QUIT), Parser.parseCommand("quit"));
    }

    @Test
    public void parseCommand_quitAliases_returnQuit() throws StockHolmException {
        for (String alias : new String[]{"close", "exit", "bye"}) {
            assertEquals(new Command(CommandType.QUIT), Parser.parseCommand(alias),
                    "alias should map to quit: " + alias);
        }
    }

    @Test
    public void parseCommand_surroundingWhitespace_isIgnored() throws StockHolmException {
        assertEquals(new Command(CommandType.QUIT), Parser.parseCommand("   quit   "));
    }

    @Test
    public void parseCommand_invAdd_returnsInvAddWithName() throws StockHolmException {
        assertEquals(invCommand(CommandType.INV_ADD, "Shop"), Parser.parseCommand("inv add Shop"));
    }

    @Test
    public void parseCommand_invAddMultiWordName_keepsWholeName() throws StockHolmException {
        Command command = Parser.parseCommand("inv add   Main   Warehouse  ");
        // Only the outer whitespace is trimmed; inner spaces in the name are kept.
        assertEquals(invCommand(CommandType.INV_ADD, "Main   Warehouse"), command);
    }

    @Test
    public void parseCommand_invDelete_returnsInvDeleteWithName() throws StockHolmException {
        assertEquals(invCommand(CommandType.INV_DELETE, "Shop"),
                Parser.parseCommand("inv delete Shop"));
    }

    @Test
    public void parseCommand_invList_returnsInvList() throws StockHolmException {
        assertEquals(new Command(CommandType.INV_LIST), Parser.parseCommand("inv list"));
    }

    @Test
    public void parseCommand_invAddWithoutName_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("inv add"));
        assertEquals("Missing inventory name. Usage: inv add NAME", e.getMessage());
    }

    @Test
    public void parseCommand_invDeleteWithoutName_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("inv delete   "));
        assertEquals("Missing inventory name. Usage: inv delete NAME", e.getMessage());
    }

    @Test
    public void parseCommand_invWithoutSubcommand_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("inv"));
        assertEquals("Usage: inv add|delete|list [NAME]", e.getMessage());
    }

    @Test
    public void parseCommand_invUnknownSubcommand_throwsUsage() {
        assertThrows(StockHolmException.class, () -> Parser.parseCommand("inv rename Shop"));
    }

    @Test
    public void parseCommand_unknownCommand_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("dance now"));
        assertTrue(e.getMessage().contains("dance"));
    }

    @Test
    public void parseCommand_uppercaseCommand_isCaseSensitive() {
        // Documents current behaviour: command words are case-sensitive.
        assertThrows(StockHolmException.class, () -> Parser.parseCommand("QUIT"));
    }

    // ---------- enter / back ----------

    @Test
    public void parseCommand_enter_returnsEnterWithName() throws StockHolmException {
        assertEquals(invCommand(CommandType.ENTER, "Main Warehouse"), Parser.parseCommand("enter Main Warehouse"));
    }

    @Test
    public void parseCommand_enterWithoutName_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("enter"));
        assertEquals("Missing inventory name. Usage: enter NAME", e.getMessage());
    }

    @Test
    public void parseCommand_back_returnsBack() throws StockHolmException {
        assertEquals(new Command(CommandType.BACK), Parser.parseCommand("back"));
    }

    // ---------- item add ----------

    /** Builds the expected {@code item add} command with the given arguments. */
    private static Command itemAdd(Map<ArgKey, String> args) {
        return new Command(CommandType.ITEM_ADD, args);
    }

    @Test
    public void parseCommand_itemAddQuotedNameWithOptions_parsesAll() throws StockHolmException {
        Command command = Parser.parseCommand("item add \"A4 Paper Case\" --type=\"Stationery\" --count=2");
        assertEquals(itemAdd(Map.of(ArgKey.ITEM_NAME, "A4 Paper Case",
                ArgKey.ITEM_TYPE, "Stationery", ArgKey.ITEM_COUNT, "2")), command);
    }

    @Test
    public void parseCommand_itemAddOptionsInAnyOrder_parsesAll() throws StockHolmException {
        Command command = Parser.parseCommand("item add \"Rice\" --count=2.5 --type=Food");
        assertEquals(itemAdd(Map.of(ArgKey.ITEM_NAME, "Rice",
                ArgKey.ITEM_TYPE, "Food", ArgKey.ITEM_COUNT, "2.5")), command);
    }

    @Test
    public void parseCommand_itemAddQuotedTypeWithSpaces_keepsSpaces() throws StockHolmException {
        Command command = Parser.parseCommand("item add Stapler --type=\"Office Supplies\"");
        assertEquals(itemAdd(Map.of(ArgKey.ITEM_NAME, "Stapler", ArgKey.ITEM_TYPE, "Office Supplies")), command);
    }

    @Test
    public void parseCommand_itemAddUnquotedName_runsUntilFirstOption() throws StockHolmException {
        Command command = Parser.parseCommand("item add Blue Pen --count=3");
        assertEquals(itemAdd(Map.of(ArgKey.ITEM_NAME, "Blue Pen", ArgKey.ITEM_COUNT, "3")), command);
    }

    @Test
    public void parseCommand_itemAddNameOnly_hasNoOptionalArgs() throws StockHolmException {
        Command command = Parser.parseCommand("item add \"A4 Paper Case\"");
        assertEquals(itemAdd(Map.of(ArgKey.ITEM_NAME, "A4 Paper Case")), command);
    }

    @Test
    public void parseCommand_itemAddWithoutName_throwsException() {
        for (String input : new String[]{"item add", "item add \"\" --count=2", "item add --count=2"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input),
                    "should fail: " + input);
            assertTrue(e.getMessage().startsWith("Missing item name."), input);
        }
    }

    @Test
    public void parseCommand_itemAddUnclosedQuote_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item add \"A4 Paper Case --count=2"));
        assertTrue(e.getMessage().startsWith("Missing closing quote"));
    }

    @Test
    public void parseCommand_itemAddInvalidCount_throwsException() {
        for (String count : new String[]{"0", "-1", "abc", "2.", "1e3", "NaN", ""}) {
            assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("item add Pen --count=" + count), "should fail: " + count);
        }
    }

    @Test
    public void parseCommand_itemAddEmptyType_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item add Pen --type=\"  \""));
        assertTrue(e.getMessage().startsWith("Missing item type."));
    }

    @Test
    public void parseCommand_itemAddUnknownOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item add Pen --colour=blue"));
        assertTrue(e.getMessage().startsWith("Unknown option --colour"));
    }

    @Test
    public void parseCommand_itemAddRepeatedOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item add Pen --count=1 --count=2"));
        assertEquals("Option given more than once: --count", e.getMessage());
    }

    @Test
    public void parseCommand_itemAddTextAfterQuotedName_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item add \"Pen\" blue --count=1"));
        assertTrue(e.getMessage().startsWith("Invalid option: blue"));
    }

    // ---------- item list / delete ----------

    @Test
    public void parseCommand_itemList_returnsItemList() throws StockHolmException {
        assertEquals(new Command(CommandType.ITEM_LIST), Parser.parseCommand("item list"));
    }

    @Test
    public void parseCommand_itemDelete_returnsItemDeleteWithIndex() throws StockHolmException {
        assertEquals(new Command(CommandType.ITEM_DELETE, Map.of(ArgKey.ITEM_INDEX, "3")),
                Parser.parseCommand("item delete 3"));
    }

    @Test
    public void parseCommand_itemDeleteWithoutIndex_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("item delete"));
        assertEquals("Missing item number. Usage: item delete INDEX", e.getMessage());
    }

    @Test
    public void parseCommand_itemDeleteInvalidIndex_throwsException() {
        for (String index : new String[]{"0", "-1", "abc", "1.5", "99999999999"}) {
            assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("item delete " + index), "should fail: " + index);
        }
    }

    @Test
    public void parseCommand_itemUnknownSubcommand_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("item edit 1"));
        assertEquals("Usage: item add|list|delete ...", e.getMessage());
    }

    // ---------- stock ----------

    @Test
    public void parseCommand_stockWithoutName_returnsStockWithoutArgs() throws StockHolmException {
        assertEquals(new Command(CommandType.STOCK), Parser.parseCommand("stock"));
    }

    @Test
    public void parseCommand_stockWithName_returnsStockWithName() throws StockHolmException {
        assertEquals(invCommand(CommandType.STOCK, "Main Warehouse"), Parser.parseCommand("stock Main Warehouse"));
    }

    @Test
    public void parseCommand_stockWithQuotedName_removesQuotes() throws StockHolmException {
        assertEquals(invCommand(CommandType.STOCK, "Main Warehouse"),
                Parser.parseCommand("stock \"Main Warehouse\""));
    }

    @Test
    public void parseCommand_stockWithNameAndBelow_returnsBothArgs() throws StockHolmException {
        Command expected = new Command(CommandType.STOCK,
                Map.of(ArgKey.INV_NAME, "Shop", ArgKey.STOCK_BELOW, "2.5"));
        assertEquals(expected, Parser.parseCommand("stock Shop --below=2.5"));
    }

    @Test
    public void parseCommand_stockBelowWithoutName_returnsThresholdOnly() throws StockHolmException {
        assertEquals(new Command(CommandType.STOCK, Map.of(ArgKey.STOCK_BELOW, "5")),
                Parser.parseCommand("stock --below=5"));
    }

    @Test
    public void parseCommand_stockInvalidThreshold_throwsException() {
        for (String threshold : new String[]{"0", "-1", "abc", "2.", "1e3", ""}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("stock Shop --below=" + threshold), "should fail: " + threshold);
            assertTrue(e.getMessage().startsWith("Threshold must be a positive number")
                    || e.getMessage().startsWith("Invalid option"), e.getMessage());
        }
    }

    @Test
    public void parseCommand_stockUnknownOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("stock Shop --above=3"));
        assertEquals("Unknown option --above. Usage: stock [NAME] [--below=COUNT]", e.getMessage());
    }

    @Test
    public void parseCommand_stockUnclosedQuote_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("stock \"Main Warehouse"));
        assertTrue(e.getMessage().startsWith("Missing closing quote in inventory name"));
    }
}
