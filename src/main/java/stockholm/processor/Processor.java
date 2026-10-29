package stockholm.processor;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import stockholm.command.Command;
import stockholm.command.CommandType;
import stockholm.command.Location;
import stockholm.exceptions.StockHolmException;
import stockholm.state.AppState;

/**
 * Runs a parsed {@link Command} against the application state and reports what happened
 * as a {@link Result}. It enforces business rules (e.g. no duplicate inventory names), but
 * it never reads input, parses strings or prints. Display is the Printer's job.
 *
 * <p>It holds no state of its own. The caller owns the data and passes it in.
 *
 * <p>This class only dispatches: it checks the command's location, then calls the {@link CommandHandler}
 * registered for its type in {@link #HANDLERS}. The handlers are grouped by area in
 * {@link InventoryHandlers}, {@link ItemHandlers}, {@link OrderHandlers}, {@link StockHandlers} and
 * {@link FindHandlers}.
 *
 * <p>Location rules: each {@link CommandType} declares where it may be used, and {@link #process} checks it
 * before running the command. {@code inv} commands only work outside an inventory, and {@code item}
 * commands only work inside one. {@code enter} works anywhere, switching directly if needed.
 * {@code help} works anywhere and changes nothing.
 * {@code stock} works anywhere: with a name it shows that inventory, without one the current inventory.
 */
public class Processor {
    /** Shown by {@code help}: every command, grouped by where it can be used. */
    private static final String HELP_MESSAGE = buildHelpMessage();
    /** The handler for each command type. A new command must be registered in {@link #createHandlers()}. */
    private static final Map<CommandType, CommandHandler> HANDLERS = createHandlers();

    /**
     * Executes one command.
     *
     * @param command a command produced by the Parser
     * @param state   the application's data; the command may modify it
     * @return the message to display and whether the program should exit
     * @throws StockHolmException if the command breaks a business rule
     */
    public static Result process(Command command, AppState state) throws StockHolmException {
        StateGuards.checkLocation(command.type().location(), state);
        CommandHandler handler = HANDLERS.get(command.type());
        if (handler == null) {
            throw new StockHolmException("Command not supported yet: " + command.type());
        }
        return handler.handle(command, state);
    }

    /** Registers the handler for every command type. */
    private static Map<CommandType, CommandHandler> createHandlers() {
        Map<CommandType, CommandHandler> handlers = new EnumMap<>(CommandType.class);
        handlers.put(CommandType.NO_OP, (command, state) -> new Result("", false));
        handlers.put(CommandType.QUIT, (command, state) -> new Result("", true));
        handlers.put(CommandType.HELP, (command, state) -> new Result(HELP_MESSAGE, false));
        handlers.put(CommandType.INV_ADD, InventoryHandlers::add);
        handlers.put(CommandType.INV_DELETE, InventoryHandlers::delete);
        handlers.put(CommandType.INV_LIST, InventoryHandlers::list);
        handlers.put(CommandType.ENTER, InventoryHandlers::enter);
        handlers.put(CommandType.BACK, InventoryHandlers::back);
        handlers.put(CommandType.ITEM_ADD, ItemHandlers::add);
        handlers.put(CommandType.ITEM_LIST, ItemHandlers::list);
        handlers.put(CommandType.ITEM_DELETE, ItemHandlers::delete);
        handlers.put(CommandType.ITEM_IMPORT, ItemHandlers::importCsv);
        handlers.put(CommandType.ITEM_EDIT, ItemHandlers::edit);
        handlers.put(CommandType.IMPORT, OrderHandlers::createImport);
        handlers.put(CommandType.EXPORT, OrderHandlers::createExport);
        handlers.put(CommandType.TRANSFER, OrderHandlers::createTransfer);
        handlers.put(CommandType.ORDER_LIST, OrderHandlers::list);
        handlers.put(CommandType.APPROVE, OrderHandlers::approve);
        handlers.put(CommandType.REJECT, OrderHandlers::reject);
        handlers.put(CommandType.CANCEL, OrderHandlers::cancel);
        handlers.put(CommandType.DELIVER, OrderHandlers::deliver);
        handlers.put(CommandType.STOCK, StockHandlers::show);
        handlers.put(CommandType.FIND, FindHandlers::find);
        handlers.put(CommandType.ITEM_MIN, ItemHandlers::setMinimum);
        handlers.put(CommandType.RESTOCK, StockHandlers::restock);
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Lists every command with its syntax and description, grouped by {@link Location}.
     * Commands without a usage (e.g. {@code NO_OP}) are left out.
     */
    private static String buildHelpMessage() {
        StringBuilder help = new StringBuilder("Commands (CAPS are values you fill in, [ ] is optional):");
        for (Location location : Location.values()) {
            help.append("\n").append(location.helpHeading());
            for (CommandType type : CommandType.values()) {
                if (type.location() == location && type.usage() != null) {
                    help.append("\n  ").append(type.usage()).append(" - ").append(type.description());
                }
            }
        }
        return help.toString();
    }
}
