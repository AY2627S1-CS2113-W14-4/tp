package stockholm.processor;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.state.AppState;

/**
 * Runs one kind of command. {@link Processor} keeps one handler per {@link stockholm.command.CommandType}
 * and has already checked the command's location before calling it.
 */
@FunctionalInterface
interface CommandHandler {
    /**
     * Runs the command.
     *
     * @param command the parsed command, with the arguments its type needs
     * @param state   the application's data; the command may modify it
     * @return the message to display and whether the program should exit
     * @throws StockHolmException if the command breaks a business rule
     */
    Result handle(Command command, AppState state) throws StockHolmException;
}
