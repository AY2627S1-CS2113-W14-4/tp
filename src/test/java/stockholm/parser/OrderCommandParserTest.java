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
 * Tests {@code OrderCommandParser}, which parses {@code order}, {@code import}, {@code export}, {@code transfer} and
 * the order state commands, through {@link Parser#parseCommand(String)}.
 */
class OrderCommandParserTest {
    @Test
    public void parseCommand_transferOptions_returnsTransfer() throws StockHolmException {
        assertEquals(new Command(CommandType.TRANSFER, Map.of(
                ArgKey.ITEM_INDEX, "2", ArgKey.ITEM_COUNT, "0.5",
                ArgKey.INV_FROM, "Main Warehouse", ArgKey.INV_TO, "Shop")),
                Parser.parseCommand("transfer 2 --to=Shop --count=0.5 --from=\"Main Warehouse\""));
        assertEquals(new Command(CommandType.TRANSFER, Map.of(ArgKey.ITEM_INDEX, "1")),
                Parser.parseCommand("transfer 1"));
        assertEquals("Shop", Parser.parseCommand("transfer 1 --from=Shop").getArg(ArgKey.INV_FROM));
        assertEquals("Shop", Parser.parseCommand("transfer 1 --to=Shop").getArg(ArgKey.INV_TO));
    }

    @Test
    public void parseCommand_transferInvalidArguments_throwsException() {
        for (String input : new String[]{"transfer", "transfer 0", "transfer -1", "transfer abc",
            "transfer 1.5", "transfer 2147483648", "transfer 1 2", "transfer 1 --count=0",
            "transfer 1 --count=-1", "transfer 1 --count=NaN", "transfer 1 --count=Infinity",
            "transfer 1 --count=1e309", "transfer 1 --count=" + "9".repeat(400),
            "transfer 1 --count=1 --count=2", "transfer 1 --to=A --to=B", "transfer 1 --from=A --from=B",
            "transfer 1 --type=Food", "transfer 1 --to=\"\"", "transfer 1 --from=\"   \"",
            "transfer 1 --to=\"Unclosed", "transfer 1 --from=", "transfer 1 --to:Shop"}) {
            assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), input);
        }
    }

    @Test
    public void parseCommand_approveValidOrderNumber_returnsApprove() throws StockHolmException {
        assertEquals(new Command(CommandType.APPROVE, Map.of(ArgKey.ORDER_INDEX, "2")),
                Parser.parseCommand("  approve   2  "));
    }

    @Test
    public void parseCommand_approveWithoutOrderNumber_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("approve"));
        assertEquals("Missing order number. Usage: approve INDEX", e.getMessage());
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
    public void parseCommand_rejectValidOrderNumberAndReason_returnsReject() throws StockHolmException {
        assertEquals(new Command(CommandType.REJECT,
                        Map.of(ArgKey.ORDER_INDEX, "2", ArgKey.ORDER_REASON, "Damaged goods")),
                Parser.parseCommand("  reject   2   Damaged goods  "));
        assertEquals(new Command(CommandType.REJECT,
                        Map.of(ArgKey.ORDER_INDEX, "1", ArgKey.ORDER_REASON, "Out of stock")),
                Parser.parseCommand("reject 1 \"Out of stock\""));
        assertEquals(new Command(CommandType.REJECT,
                        Map.of(ArgKey.ORDER_INDEX, "3", ArgKey.ORDER_REASON, "Damaged in transit")),
                Parser.parseCommand("reject 3 --reason=\"Damaged in transit\""));
    }

    @Test
    public void parseCommand_rejectWithoutOrderNumber_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("reject"));
        assertEquals("Missing order number. Usage: reject INDEX REASON", e.getMessage());
    }

    @Test
    public void parseCommand_rejectWithoutReason_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("reject 1"));
        assertEquals("Missing rejection reason. Usage: reject INDEX REASON", e.getMessage());
    }

    @Test
    public void parseCommand_rejectInvalidOrderNumber_throwsException() {
        for (String index : new String[]{"0", "-1", "1.5", "abc", "2147483648"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("reject " + index + " reason"));
            assertEquals("Order number must be a positive whole number: " + index, e.getMessage());
        }
    }

    @Test
    public void parseCommand_rejectUnclosedQuote_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("reject 1 \"unclosed quote"));
        assertEquals("Missing closing quote in rejection reason. Usage: reject INDEX REASON", e.getMessage());
    }

    @Test
    public void parseCommand_cancelValidOrderNumber_returnsCancel() throws StockHolmException {
        assertEquals(new Command(CommandType.CANCEL, Map.of(ArgKey.ORDER_INDEX, "2")),
                Parser.parseCommand("  cancel   2  "));
    }

    @Test
    public void parseCommand_cancelWithoutOrderNumber_throwsUsage() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("cancel"));
        assertEquals("Missing order number. Usage: cancel INDEX", e.getMessage());
    }

    @Test
    public void parseCommand_cancelInvalidOrderNumber_throwsException() {
        for (String index : new String[]{"0", "-1", "1.5", "abc", "1 2", "2147483648"}) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("cancel " + index));
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
        assertEquals("Missing order number. Usage: deliver INDEX", e.getMessage());
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
                    Parser.parseCommand("export 2 \t--count=" + count));
        }
    }

    @Test
    public void parseCommand_exportWithoutItemNumber_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand("export"));
        assertEquals("Missing item number. Usage: export INDEX [--count=COUNT]", e.getMessage());
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
        String[] invalidCounts = {"0", "0.0", "-1", "abc", "NaN", "Infinity", "1e3",
            "9".repeat(400), "0." + "0".repeat(400) + "1"};
        for (String count : invalidCounts) {
            StockHolmException e = assertThrows(StockHolmException.class,
                    () -> Parser.parseCommand("export 1 --count=" + count));
            assertEquals("Count must be a positive number, e.g. 2 or 2.5: " + count, e.getMessage());
        }
    }

    @Test
    public void parseCommand_exportPositionalCount_throwsException() {
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("export 1 2"));
        assertEquals("Invalid option: 2. Usage: export INDEX [--count=COUNT]", e.getMessage());
    }

    @Test
    public void parseCommand_exportBadOptions_throwsException() {
        StockHolmException unknown = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("export 1 --type=Food"));
        assertEquals("Unknown option --type. Usage: export INDEX [--count=COUNT]", unknown.getMessage());

        StockHolmException repeated = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("export 1 --count=1 --count=2"));
        assertEquals("Option given more than once: --count", repeated.getMessage());
    }

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

    @Test
    public void parseCommand_orderList_returnsOrderList() throws StockHolmException {
        assertEquals(new Command(CommandType.ORDER_LIST), Parser.parseCommand("order list"));
        assertEquals(new Command(CommandType.ORDER_LIST), Parser.parseCommand("  order   list  "));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_PAGE, "2")),
                Parser.parseCommand("order list 2"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_PAGE, "2", ArgKey.ORDER_PAGE_SIZE, "5")),
                Parser.parseCommand("order list 2 --size=5"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_PAGE_SIZE, "5")),
                Parser.parseCommand("order list --size=5"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_PAGE, "3", ArgKey.ORDER_PAGE_SIZE, "8")),
                Parser.parseCommand("order list 3 --limit=8"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_PAGE, "4", ArgKey.ORDER_PAGE_SIZE, "6")),
                Parser.parseCommand("order list --page=4 --size=6"));
    }

    @Test
    public void parseCommand_orderMissingOrUnknownSubcommand_throwsUsage() {
        for (String input : new String[]{"order", "order add", "order LIST"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertEquals("Usage: " + CommandType.ORDER_LIST.usage(), e.getMessage());
        }
    }

    @Test
    public void parseCommand_orderListInvalidArguments_throwsException() {
        for (String input : new String[]{"order list Shop", "order list 0", "order list -1", "order list 1.5"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertTrue(e.getMessage().contains("Page number must be a positive whole number"));
        }

        StockHolmException unknownOpt = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --count=2"));
        assertEquals("Unknown option --count. Usage: " + CommandType.ORDER_LIST.usage(), unknownOpt.getMessage());

        StockHolmException zeroSize = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list 1 --size=0"));
        assertEquals("Page size must be a positive whole number: 0", zeroSize.getMessage());

        StockHolmException bothSizeAndLimit = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list 1 --size=5 --limit=5"));
        assertEquals("Cannot specify both --size and --limit. Usage: " + CommandType.ORDER_LIST.usage(),
                bothSizeAndLimit.getMessage());

        StockHolmException duplicatePage = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list 1 --page=2"));
        assertEquals("Unknown option --page. Usage: " + CommandType.ORDER_LIST.usage(), duplicatePage.getMessage());
    }

    @Test
    public void parseCommand_orderListWithFilters_returnsOrderListWithFilterArgs() throws StockHolmException {
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_STATUS, "WAITING_APPROVAL")),
                Parser.parseCommand("order list --status=waiting_approval"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_STATUS, "DELIVERED")),
                Parser.parseCommand("order list --state=delivered"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_STATUS, "CANCELED")),
                Parser.parseCommand("order list --status=cancelled"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_INV, "Shop")),
                Parser.parseCommand("order list --inv=Shop"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_INV, "Warehouse")),
                Parser.parseCommand("order list --inventory=Warehouse"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_TYPE, "import")),
                Parser.parseCommand("order list --type=import"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(ArgKey.ORDER_TYPE, "transfer")),
                Parser.parseCommand("order list --kind=transfer"));
        assertEquals(new Command(CommandType.ORDER_LIST, Map.of(
                ArgKey.ORDER_PAGE, "2",
                ArgKey.ORDER_STATUS, "WAITING_APPROVAL",
                ArgKey.ORDER_INV, "Shop",
                ArgKey.ORDER_TYPE, "export",
                ArgKey.ORDER_PAGE_SIZE, "5")),
                Parser.parseCommand("order list 2 --status=WAITING_APPROVAL --inv=Shop --type=export --size=5"));
    }

    @Test
    public void parseCommand_orderListInvalidFilterOptions_throwsException() {
        StockHolmException invalidStatus = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --status=invalid"));
        assertTrue(invalidStatus.getMessage().contains("Unknown order status: invalid"));

        StockHolmException invalidType = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --type=refund"));
        assertTrue(invalidType.getMessage().contains("Unknown order type: refund"));

        StockHolmException bothStatusAndState = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --status=DELIVERED --state=DELIVERED"));
        assertEquals("Cannot specify both --status and --state. Usage: " + CommandType.ORDER_LIST.usage(),
                bothStatusAndState.getMessage());

        StockHolmException bothInvAndInventory = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --inv=Shop --inventory=Shop"));
        assertEquals("Cannot specify both --inv and --inventory. Usage: " + CommandType.ORDER_LIST.usage(),
                bothInvAndInventory.getMessage());

        StockHolmException bothTypeAndKind = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("order list --type=import --kind=import"));
        assertEquals("Cannot specify both --type and --kind. Usage: " + CommandType.ORDER_LIST.usage(),
                bothTypeAndKind.getMessage());
    }
}
