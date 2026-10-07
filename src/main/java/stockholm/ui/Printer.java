package stockholm.ui;

import stockholm.processor.Result;

/**
 * The only class that writes to the screen. It decides how things look,
 * but never what happened. That comes from the Processor's {@link Result}.
 */
public class Printer {
    private static final int INDENT_LEVEL = 4;
    private static final String BAR_CHARACTER = "─";
    /** Bar width used when the terminal width can't be read from {@code $COLUMNS}. */
    private static final int FALLBACK_BAR_LENGTH = 100;

    /**
     * Displays the outcome of a processed command. An empty message prints nothing.
     *
     * @param result the Processor's output
     */
    public static void printResult(Result result) {
        if (!result.message().isEmpty()) {
            printIndent(result.message());
        }
    }

    /** Prints the input prompt without a trailing newline. */
    public static void printPrompt() {
        System.out.print("❯ ");
    }

    public static void printPrompt(String invName) {
        System.out.println(invName + "❯ ");
    }

    /**
     * Prints `message` indented, one indent per line so multiline strings
     * (e.g. the item list) stay aligned under the bar.
     */
    public static void printIndent(String message) {
        String indentedMessage = " ".repeat(INDENT_LEVEL) + message;
        indentedMessage = indentedMessage.replace("\n", "\n" + " ".repeat(INDENT_LEVEL));

        System.out.println(indentedMessage);
    }

    public static void printIndentedError(String message) {
        String indentedMessage = " ".repeat(INDENT_LEVEL) + message;
        indentedMessage = indentedMessage.replace("\n", "\n" + " ".repeat(INDENT_LEVEL));

        System.err.println(indentedMessage);
    }

    public static void printBar() {
        System.out.println(BAR_CHARACTER.repeat(getTerminalWidth()));
    }

    private static int getTerminalWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns == null) {
            return FALLBACK_BAR_LENGTH;
        }
        try {
            return Integer.parseInt(columns.trim());
        } catch (NumberFormatException e) {
            return FALLBACK_BAR_LENGTH;
        }
    }
}
