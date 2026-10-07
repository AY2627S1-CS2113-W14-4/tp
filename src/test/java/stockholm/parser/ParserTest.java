package stockholm.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.exceptions.StockHolmException;

/**
 * Tests for {@link Parser#parseCommand(String)}.
 * The private helpers (splitFirstWord, parseInvCommand, requireName) are covered indirectly
 * through the public method, which is the usual way to test private code.
 */
class ParserTest {
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
        assertEquals(new Command(CommandType.INV_ADD, "Shop"), Parser.parseCommand("inv add Shop"));
    }

    @Test
    public void parseCommand_invAddMultiWordName_keepsWholeName() throws StockHolmException {
        Command command = Parser.parseCommand("inv add   Main   Warehouse  ");
        // Only the outer whitespace is trimmed; inner spaces in the name are kept.
        assertEquals(new Command(CommandType.INV_ADD, "Main   Warehouse"), command);
    }

    @Test
    public void parseCommand_invDelete_returnsInvDeleteWithName() throws StockHolmException {
        assertEquals(new Command(CommandType.INV_DELETE, "Shop"),
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
}
