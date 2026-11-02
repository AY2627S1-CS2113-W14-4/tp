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
 * Tests {@link Parser#parseCommand(String)} on input that is not specific to one area:
 * empty input, {@code quit}, {@code help}, unknown commands and whitespace.
 * Each area's commands are tested in {@link InventoryCommandParserTest},
 * {@link ItemCommandParserTest} and {@link OrderCommandParserTest}; the shared helpers
 * in {@link ArgumentParserTest}.
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
    public void parseCommand_noArgumentCommands_rejectExtraTextButAllowWhitespace() throws StockHolmException {
        Map<String, CommandType> commands = Map.of(
                "quit", CommandType.QUIT, "bye", CommandType.QUIT,
                "exit", CommandType.QUIT, "close", CommandType.QUIT,
                "back", CommandType.BACK, "inv list", CommandType.INV_LIST,
                "item list", CommandType.ITEM_LIST, "help", CommandType.HELP);
        for (Map.Entry<String, CommandType> entry : commands.entrySet()) {
            String input = entry.getKey();
            CommandType type = entry.getValue();
            assertEquals(new Command(type), Parser.parseCommand("  " + input + " \t"), input);
            for (String extra : new String[]{" unexpected", " --count=2"}) {
                StockHolmException error = assertThrows(StockHolmException.class,
                        () -> Parser.parseCommand(input + extra), input + extra);
                assertEquals("Usage: " + type.usage(), error.getMessage());
            }
        }
    }

    @Test
    public void parseCommand_tabsBetweenWords_splitLikeSpaces() throws StockHolmException {
        assertEquals(invCommand(CommandType.INV_ADD, "Shop"), Parser.parseCommand("inv\tadd \t Shop"));
        assertEquals(new Command(CommandType.ITEM_LIST), Parser.parseCommand("item\tlist"));
    }

    @Test
    public void parseCommand_help_returnsHelp() throws StockHolmException {
        assertEquals(new Command(CommandType.HELP), Parser.parseCommand("help"));
        assertEquals(new Command(CommandType.HELP), Parser.parseCommand("  help  "));
    }

    @Test
    public void parseCommand_helpWithArguments_throwsUsage() {
        for (String input : new String[]{"help item", "help --count=2"}) {
            StockHolmException e = assertThrows(StockHolmException.class, () -> Parser.parseCommand(input));
            assertEquals("Usage: help", e.getMessage());
        }
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

    /** Builds the command expected for an {@code inv} subcommand that takes an inventory name. */
    private static Command invCommand(CommandType type, String inventoryName) {
        return new Command(type, Map.of(ArgKey.INV_NAME, inventoryName));
    }
}
