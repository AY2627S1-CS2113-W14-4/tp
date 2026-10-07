package stockholm.ui;

/**
 * Does complex UI actions
 */
public class Ui {
    /**
     * Shows the prompt between two bars and returns the user's raw line.
     *
     * @return the line exactly as typed, unmodified
     */
    public static String readInputFancy() {
        Printer.printBar();
        Printer.printPrompt();
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
