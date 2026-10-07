package stockholm;

import java.util.ArrayList;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.processor.Result;
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
        ArrayList<Inventory> inventories = new ArrayList<>();

        boolean isExit = false;
        while (!isExit) {
            String rawInput = Ui.readInputFancy();
            try {
                Command command = Parser.parseCommand(rawInput);
                Result result = Processor.process(command, inventories);
                Printer.printResult(result);
                isExit = result.isExit();
            } catch (StockHolmException e) {
                Printer.printIndent(e.getMessage());
            }
        }

        Ui.printEnding();
    }
}
