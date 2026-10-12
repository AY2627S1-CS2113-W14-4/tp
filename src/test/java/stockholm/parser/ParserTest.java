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
 * Tests for {@link Parser#parseCommand(String)} Corp.
 * The private helpers (splitFirstWord, parseInvCommand, parseOptions, requireArg, ...) are covered indirectly
 * through the public method, which is the usual way to test private code.
 */
class ParserTest {
    @Test
    public void parseCommand_approveValidOrderNumber_returnsApprove() throws StockHolmException {
        assertEquals(new Command(CommandType.APPROVE, Map.of(ArgKey.ORDER_INDEX, "2")),
                Parser.parseCommand("  approve   2  "));
    }

    @Test
    public void parseCommand_approveWithoutOrderNumber_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("approve"));
        assertEquals("Missing order number. Usage: approve ORDER_ID", e.getMessage());
    }

    @Test
    public void parseCommand_approveInvalidOrderNumber_throwsException() {
        for (String index : new String[]{"0", "-1", "1.5", "abc", "1 2", "2147483648"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("approve " + index));
            assertEquals("Order number must be a positive whole number: " + index, e.getMessage());
        }
    }

    @Test
    public void parseCommand_deliverValidOrderNumber_returnsDeliver() throws StockHolmException {
        assertEquals(new Command(CommandType.DELIVER, Map.of(ArgKey.ORDER_INDEX, "2")),
                Parser.parseCommand("  deliver   2  "));
    }

    @Test
    public void parseCommand_deliverWithoutOrderNumber_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("deliver"));
        assertEquals("Missing order number. Usage: deliver ORDER_ID", e.getMessage());
    }

    @Test
    public void parseCommand_deliverInvalidOrderNumber_throwsException() {
        for (String index : new String[]{"0", "-1", "1.5", "abc", "1 2", "2147483648"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("deliver " + index));
            assertEquals("Order number must be a positive whole number: " + index, e.getMessage());
        }
    }

    @Test
    public void parseCommand_exportWithoutCount_leavesCountAbsent() throws StockHolmException {
        assertEquals(new Command(CommandType.EXPORT, Map.of(ArgKey.ITEM_INDEX, "2")),
                Parser.parseCommand("  export  2  "));
    }

    @Test
    public void parseCommand_exportWithCount_preservesCount() throws StockHolmException {
        for (String count : new String[]{"1", "2.5", "0.25"}) {
            assertEquals(new Command(CommandType.EXPORT,
                    Map.of(ArgKey.ITEM_INDEX, "2", ArgKey.ITEM_COUNT, count)),
                    Parser.parseCommand("export 2 \t" + count));
        }
    }

    @Test
    public void parseCommand_exportWithoutItemNumber_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("export"));
        assertEquals("Missing item number. Usage: export ITEM_ID [--count=COUNT]", e.getMessage());
    }

    @Test
    public void parseCommand_exportInvalidItemNumber_throwsException() {
        for (String index : new String[]{"0", "-1", "1.5", "abc", "2147483648", "99999999999999999999"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("export " + index));
            assertEquals("Item number must be a positive whole number: " + index, e.getMessage());
        }
    }

    @Test
    public void parseCommand_exportInvalidCount_throwsException() {
        String[] invalidCounts = {"0", "0.0", "-1", "abc", "NaN", "Infinity", "1e3", "--count=2",
            "9".repeat(400), "0." + "0".repeat(400) + "1"};
        for (String count : invalidCounts) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("export 1 " + count));
            assertEquals("Count must be a positive finite number, e.g. 2 or 2.5: " + count, e.getMessage());
        }
    }

    @Test
    public void parseCommand_exportExtraArguments_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("export 1 2 3"));
        assertEquals("Usage: export ITEM_ID [--count=COUNT]", e.getMessage());
    }

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

    // ---------- import ----------

    @Test
    public void parseCommand_importValidItemData_matchesItemAddArguments() throws StockHolmException {
        String[] itemData = {
            "\"A4 Paper Case\" --type=\"Stationery\" --count=2",
            "Rice --count=2.5 --type=Food",
            "Stapler --type=\"Office Supplies\"",
            "Blue Pen --count=3",
            "\"A4 Paper Case\"",
            "Pen",
        };
        for (String data : itemData) {
            Command expected = new Command(CommandType.IMPORT, Parser.parseCommand("item add " + data).args());
            assertEquals(expected, Parser.parseCommand("import " + data), data);
        }
    }

    @Test
    public void parseCommand_importWithoutName_throwsImportUsage() {
        for (String input : new String[]{"import", "import \"\" --count=2", "import --count=2"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertEquals("Missing item name. Usage: import NAME [--type=TYPE] [--count=COUNT]", e.getMessage());
        }
    }

    @Test
    public void parseCommand_importUnclosedNameQuote_throwsImportUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("import \"Unclosed --count=2"));
        assertEquals("Missing closing quote in item name. Usage: import NAME [--type=TYPE] [--count=COUNT]",
                e.getMessage());
    }

    @Test
    public void parseCommand_importEmptyType_throwsImportUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("import Pen --type=\"  \""));
        assertEquals("Missing item type. Usage: import NAME [--type=TYPE] [--count=COUNT]", e.getMessage());
    }

    @Test
    public void parseCommand_importUnknownOption_throwsImportUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("import Pen --colour=blue"));
        assertEquals("Unknown option --colour. Usage: import NAME [--type=TYPE] [--count=COUNT]", e.getMessage());
    }

    @Test
    public void parseCommand_importRepeatedOption_throwsException() {
        for (String option : new String[]{"count=1", "type=Food"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("import Pen --" + option + " --" + option));
            assertTrue(e.getMessage().startsWith("Option given more than once:"));
        }
    }

    @Test
    public void parseCommand_importMalformedOption_throwsImportUsage() {
        for (String data : new String[]{"\"Pen\" blue", "Pen --count", "Pen --type=\"Unclosed"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("import " + data));
            assertTrue(e.getMessage().startsWith("Invalid option:"));
            assertTrue(e.getMessage().endsWith("Usage: import NAME [--type=TYPE] [--count=COUNT]"));
        }
    }

    // ---------- order list ----------

    @Test
    public void parseCommand_orderList_returnsOrderList() throws StockHolmException {
        assertEquals(new Command(CommandType.ORDER_LIST), Parser.parseCommand("order list"));
        assertEquals(new Command(CommandType.ORDER_LIST), Parser.parseCommand("  order   list  "));
    }

    @Test
    public void parseCommand_orderMissingOrUnknownSubcommand_throwsUsage() {
        for (String input : new String[]{"order", "order add", "order LIST"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertEquals("Usage: order list", e.getMessage());
        }
    }

    @Test
    public void parseCommand_orderListWithArguments_throwsUsage() {
        for (String input : new String[]{"order list Shop", "order list --count=2"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertEquals("Usage: order list", e.getMessage());
        }
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

}
