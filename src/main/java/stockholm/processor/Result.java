package stockholm.processor;

/**
 * The output of the Processor and the input of the Printer.
 * It describes what happened, but leaves how to display it to the Printer.
 *
 * @param message text to show the user (may be empty)
 * @param isExit  {@code true} if the program should stop after this command
 */
public record Result(String message, boolean isExit) {
}
