package stockholm.exceptions;

/**
 * Signals a user-facing error in StockHolm.
 * The Parser throws it for bad syntax and the Processor throws it for broken business rules,
 * so the main loop needs only one catch block. The message is shown to the user as-is.
 */
public class StockHolmException extends Exception {
    /**
     * Creates an exception carrying a message meant to be shown to the user.
     *
     * @param message human-readable description of what went wrong
     */
    public StockHolmException(String message) {
        super(message);
    }
}
