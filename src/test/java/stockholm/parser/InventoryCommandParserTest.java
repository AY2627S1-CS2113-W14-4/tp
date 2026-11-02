package stockholm.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import stockholm.command.ArgKey;
import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Tests {@code InventoryCommandParser}, which parses {@code inv}, {@code enter} and {@code back}, through {@link
 * Parser#parseCommand(String)}.
 */
class InventoryCommandParserTest {
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
    public void parseCommand_inventoryNames_removeQuotesAndTrimEdges() throws StockHolmException {
        Map<String, CommandType> commands = Map.of(
                "inv add", CommandType.INV_ADD, "inv delete", CommandType.INV_DELETE,
                "enter", CommandType.ENTER, "stock", CommandType.STOCK, "restock", CommandType.RESTOCK);
        for (Map.Entry<String, CommandType> entry : commands.entrySet()) {
            for (String name : new String[]{"New   Shop", "\"New   Shop\"", "\"  New   Shop \t\""}) {
                assertEquals(invCommand(entry.getValue(), "New   Shop"),
                        Parser.parseCommand(entry.getKey() + " " + name), entry.getKey() + " " + name);
            }
        }
        assertEquals("New Shop", Parser.parseCommand("stock \" New Shop \" --below=5")
                .getArg(ArgKey.INV_NAME));
        for (String option : new String[]{"inv", "inventory"}) {
            assertEquals("New Shop", Parser.parseCommand("order list --" + option + "=\" New Shop \"")
                    .getArg(ArgKey.ORDER_INV));
        }
        Command transfer = Parser.parseCommand("transfer 1 --from=\" New Shop \" --to=\" Warehouse \"");
        assertEquals("New Shop", transfer.getArg(ArgKey.INV_FROM));
        assertEquals("Warehouse", transfer.getArg(ArgKey.INV_TO));
    }

    @Test
    public void parseCommand_requiredInventoryNameBlankOrMalformed_throwsException() {
        for (String command : new String[]{"inv add", "inv delete", "enter"}) {
            for (String name : new String[]{"\"\"", "\" \t \"", "\"Unclosed", "\"Shop\" extra"}) {
                String input = command + " " + name;
                assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), input);
            }
        }
    }

    @Test
    public void parseCommand_inventoryNameWithUnexpectedQuote_throwsWithLabelAndUsage() {
        Map<String, CommandType> commands = Map.of(
                "inv add", CommandType.INV_ADD, "inv delete", CommandType.INV_DELETE,
                "enter", CommandType.ENTER);
        for (Map.Entry<String, CommandType> entry : commands.entrySet()) {
            for (String name : new String[]{"Shop\"", "New Shop \"", "New\" Shop", "12\" Pipes"}) {
                StockHolmException error = assertThrows(StockHolmException.class,
                        () -> Parser.parseCommand(entry.getKey() + " " + name));
                assertEquals("Unexpected \" in inventory name. Usage: " + entry.getValue().usage(),
                        error.getMessage());
            }
        }
        StockHolmException newNameError = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("inv rename x y y \""));
        assertEquals("Unexpected \" in new inventory name. Usage: " + CommandType.INV_RENAME.usage(),
                newNameError.getMessage());
        StockHolmException oldNameError = assertThrows(StockHolmException.class,
                () -> Parser.parseCommand("inv rename x\" y"));
        assertEquals("Unexpected \" in old inventory name. Usage: " + CommandType.INV_RENAME.usage(),
                oldNameError.getMessage());
    }

    @Test
    public void parseCommand_invDelete_returnsInvDeleteWithName() throws StockHolmException {
        assertEquals(invCommand(CommandType.INV_DELETE, "Shop"),
                Parser.parseCommand("inv delete Shop"));
    }

    @Test
    public void parseCommand_invRename_readsBothNames() throws StockHolmException {
        Command expected = new Command(CommandType.INV_RENAME,
                Map.of(ArgKey.INV_NAME, "Main Warehouse", ArgKey.INV_NEW_NAME, "Central Warehouse"));
        assertEquals(expected, Parser.parseCommand("inv rename \"Main Warehouse\" Central Warehouse"));
        assertEquals(new Command(CommandType.INV_RENAME,
                        Map.of(ArgKey.INV_NAME, "Shop", ArgKey.INV_NEW_NAME, "New Shop")),
                Parser.parseCommand("inv rename Shop \" New Shop \""));
        assertEquals(expected, Parser.parseCommand("inv rename \"  Main Warehouse  \" \" Central Warehouse \""));
        assertEquals(Parser.parseCommand("inv add \" New Shop \"").getArg(ArgKey.INV_NAME),
                Parser.parseCommand("inv rename Shop \" New Shop \"").getArg(ArgKey.INV_NEW_NAME));
    }

    @Test
    public void parseCommand_invRenameInvalid_throwsException() {
        for (String input : new String[]{"inv rename", "inv rename Shop", "inv rename \"\" New",
            "inv rename \"Unclosed New", "inv rename \"   \" New", "inv rename Shop \"\"",
            "inv rename Shop \"   \"", "inv rename Shop \"Unclosed", "inv rename Shop \"New\" extra"}) {
            assertThrows(StockHolmException.class, () -> Parser.parseCommand(input), input);
        }
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
        assertEquals("Usage: inv add|rename|delete|list ...", e.getMessage());
    }

    @Test
    public void parseCommand_invUnknownSubcommand_throwsUsage() {
        assertThrows(StockHolmException.class, () -> Parser.parseCommand("inv change Shop"));
    }

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

    /** Builds the command expected for an {@code inv} subcommand that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }
}
