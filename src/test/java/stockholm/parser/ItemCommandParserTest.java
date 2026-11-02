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
 * Tests {@code ItemCommandParser}, which parses {@code item}, {@code stock}, {@code find} and {@code restock}, through
 * {@link Parser#parseCommand(String)}.
 */
class ItemCommandParserTest {
    @Test
    public void parseCommand_itemImportQuotedPath_returnsImportCommand() throws StockHolmException {
        assertEquals(new Command(CommandType.ITEM_IMPORT, Map.of(ArgKey.CSV_PATH, "folder/my items.csv")),
                Parser.parseCommand("item import \"folder/my items.csv\""));
    }

    @Test
    public void parseCommand_itemImportMissingOrInvalidPath_throwsUsage() {
        for (String input : new String[]{"item import", "item import \"\"", "item import \"open.csv"}) {
            assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), input);
        }
    }

    @Test
    public void parseCommand_itemEditWithNameAndType_returnsEditCommand() throws StockHolmException {
        assertEquals(new Command(CommandType.ITEM_EDIT,
                Map.of(ArgKey.ITEM_INDEX, "2", ArgKey.ITEM_NAME, "Blue Pen", ArgKey.ITEM_TYPE, "")),
                Parser.parseCommand("item edit 2 --name=\"Blue Pen\" --type=\"\""));
    }

    @Test
    public void parseCommand_itemEditWithoutChanges_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("item edit 2"));
        assertEquals("Give a new name, a new type, or both. Usage: " + CommandType.ITEM_EDIT.usage(),
                e.getMessage());
    }

    @Test
    public void parseCommand_itemEditInvalidArguments_throwsWithoutCommand() {
        for (String input : new String[]{"item edit 0 --name=Pen", "item edit 1 --name=\" \"",
            "item edit 1 --color=Blue", "item edit 1 --name=Pen --name=Pencil"}) {
            assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), input);
        }
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
    public void parseCommand_countTooLargeToBeFinite_throwsException() {
        String huge = "9".repeat(400);
        for (String input : new String[]{"item add Pen --count=", "import Pen --count=", "export 1 --count="}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input + huge));
            assertEquals("Count must be a positive number, e.g. 2 or 2.5: " + huge, e.getMessage());
        }
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("stock Shop --below=" + huge));
        assertEquals("Threshold must be a positive number, e.g. 2 or 2.5: " + huge, e.getMessage());
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
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("item dance 1"));
        assertEquals("Usage: item add|list|delete|edit|import|min ...", e.getMessage());
    }

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
    public void parseCommand_findKeyword_returnsFindWithKeyword() throws StockHolmException {
        assertEquals(new Command(CommandType.FIND, Map.of(ArgKey.FIND_KEYWORD, "blue pen")),
                Parser.parseCommand("find blue pen"));
    }

    @Test
    public void parseCommand_findTypeOnly_returnsFindWithType() throws StockHolmException {
        assertEquals(new Command(CommandType.FIND, Map.of(ArgKey.ITEM_TYPE, "Office Supplies")),
                Parser.parseCommand("find --type=\"Office Supplies\""));
    }

    @Test
    public void parseCommand_findQuotedKeywordAndType_returnsBoth() throws StockHolmException {
        Command expected = new Command(CommandType.FIND,
                Map.of(ArgKey.FIND_KEYWORD, "pen", ArgKey.ITEM_TYPE, "Stationery"));
        assertEquals(expected, Parser.parseCommand("find \"pen\" --type=Stationery"));
    }

    @Test
    public void parseCommand_findWithoutKeywordOrType_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("find"));
        assertEquals("Give a keyword, a type, or both. Usage: find [KEYWORD] [--type=TYPE]", e.getMessage());
    }

    @Test
    public void parseCommand_findBlankType_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("find pen --type=\"  \""));
        assertTrue(e.getMessage().startsWith("Missing item type."));
    }

    @Test
    public void parseCommand_findUnknownOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("find pen --count=2"));
        assertEquals("Unknown option --count. Usage: find [KEYWORD] [--type=TYPE]", e.getMessage());
    }

    @Test
    public void parseCommand_itemMin_returnsIndexAndMinimum() throws StockHolmException {
        assertEquals(new Command(CommandType.ITEM_MIN, Map.of(ArgKey.ITEM_INDEX, "2", ArgKey.ITEM_MIN, "2.5")),
                Parser.parseCommand("item min 2 2.5"));
    }

    @Test
    public void parseCommand_itemMinZero_isAllowedToClear() throws StockHolmException {
        for (String zero : new String[]{"0", "0.0", "00"}) {
            assertEquals(new Command(CommandType.ITEM_MIN, Map.of(ArgKey.ITEM_INDEX, "1", ArgKey.ITEM_MIN, zero)),
                    Parser.parseCommand("item min 1 " + zero));
        }
    }

    @Test
    public void parseCommand_itemMinMissingArguments_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("item min"));
        assertEquals("Missing item number. Usage: item min INDEX COUNT", e.getMessage());
        e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("item min 1"));
        assertEquals("Missing minimum count. Usage: item min INDEX COUNT", e.getMessage());
    }

    @Test
    public void parseCommand_itemMinInvalidNumbers_throwsException() {
        String[] inputs = {"item min 0 5", "item min abc 5", "item min 1 -1", "item min 1 abc", "item min 1 1e3",
            "item min 1 5 6"};
        for (String input : inputs) {
            assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), "should fail: " + input);
        }
    }

    @Test
    public void parseCommand_restock_returnsOptionalName() throws StockHolmException {
        assertEquals(new Command(CommandType.RESTOCK), Parser.parseCommand("restock"));
        assertEquals(new Command(CommandType.RESTOCK, Map.of(ArgKey.INV_NAME, "Main Warehouse")),
                Parser.parseCommand("restock Main Warehouse"));
        assertEquals(new Command(CommandType.RESTOCK, Map.of(ArgKey.INV_NAME, "Main Warehouse")),
                Parser.parseCommand("restock \"Main Warehouse\""));
    }

    @Test
    public void parseCommand_restockWithOption_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("restock Shop --below=2"));
        assertEquals("restock takes no options. Usage: restock [NAME]", e.getMessage());
    }

    /** Builds the command expected for an {@code inv} subcommand that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }

    /** Builds the expected {@code item add} command with the given arguments. */
    private static Command itemAdd(Map<ArgKey, String> args) {
        return new Command(CommandType.ITEM_ADD, args);
    }
}
