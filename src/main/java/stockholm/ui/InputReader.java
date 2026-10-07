package stockholm.ui;

import java.util.Scanner;

/**
 * Reads raw user input from stdin.
 * Use {@link Ui#readInputFancy()} for the decorated version with the bar and prompt.
 */
public class InputReader {
    private static final Scanner SCANNER = new Scanner(System.in);

    /**
     * Reads the next line typed by the user and returns it unmodified.
     *
     * @return the raw line, or {@code "quit"} if stdin was closed (e.g. Ctrl+D) so the app exits cleanly
     */
    public static String readLine() {
        if (!SCANNER.hasNextLine()) {
            return "quit";
        }
        return SCANNER.nextLine();
    }
}
