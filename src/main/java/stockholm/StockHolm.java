package stockholm;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.processor.Result;
import stockholm.state.AppState;
import stockholm.storage.Storage;
import stockholm.ui.Printer;
import stockholm.ui.Ui;

public class StockHolm {
    /**
     * Loads saved inventories and orders, then runs the read-parse-process-print loop until the user exits,
     * saving after every command that changes data. Startup stops if a save file cannot be read,
     * so data that was not loaded is never overwritten.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Ui.printStarter();
        AppState state = new AppState();
        Storage storage = new Storage();
        try {
            Printer.printResult(storage.load(state));
        } catch (StockHolmException e) {
            Printer.printIndent(e.getMessage());
            return;
        }

        boolean isExit = false;
        while (!isExit) {
            Inventory current = state.getCurrentInventory();
            String rawInput = Ui.readInputFancy(current == null ? null : current.getName());
            try {
                Command command = Parser.parseCommand(rawInput);
                Result result = Processor.process(command, state);
                Printer.printResult(result);
                isExit = result.isExit();
                // Saved after printing, so a save failure does not hide what the command did.
                storage.saveAfterCommand(command, state);
            } catch (StockHolmException e) {
                Printer.printIndent(e.getMessage());
            }
        }

        Ui.printEnding();
    }
}
