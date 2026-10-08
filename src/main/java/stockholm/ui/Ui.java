package stockholm.ui;

/**
 * Does complex UI actions
 */
public class Ui {
    /**
     * Shows the prompt between two bars and returns the user's raw line.
     *
     * @param inventoryName name of the inventory the user is inside, shown in the prompt;
     *                      {@code null} when the user is not inside one
     * @return the line exactly as typed, unmodified
     */
    public static String readInputFancy(String inventoryName) {
        Printer.printBar();
        if (inventoryName == null) {
            Printer.printPrompt();
        } else {
            Printer.printPrompt(inventoryName);
        }
        String rawInput = InputReader.readLine();
        Printer.printBar();
        return rawInput;
    }

    public static void printStarter() {
        System.out.println("Hello, Welcome to StockHolm, your inventory manager program");
    }

    public static void printEnding() {
        System.out.println("See you next time");
    }
}
