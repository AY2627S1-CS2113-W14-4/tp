package stockholm;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.processor.Result;
import stockholm.state.AppState;
import stockholm.ui.Printer;
import stockholm.ui.Ui;

public class StockHolm {
    /**
     * Runs the read-parse-process-print loop until the user exits.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Ui.printStarter();
        AppState state = new AppState();

        boolean isExit = false;
        while (!isExit) {
            Inventory current = state.getCurrentInventory();
            String rawInput = Ui.readInputFancy(current == null ? null : current.getName());
            try {
                Command command = Parser.parseCommand(rawInput);
                Result result = Processor.process(command, state);
                Printer.printResult(result);
                isExit = result.isExit();
            } catch (StockHolmException e) {
                Printer.printIndent(e.getMessage());
            }
        }

        Ui.printEnding();
    }
}
