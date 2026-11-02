package stockholm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests single sessions of the whole app: typed commands, their printed results, and how the app exits. */
class SessionTest {
    @TempDir
    private Path temporaryDirectory;
    private AppRunner app;

    @BeforeEach
    public void setUp() {
        app = new AppRunner(temporaryDirectory);
    }

    @Test
    public void run_quitOnFirstLaunch_greetsAndSavesNothing() throws IOException, InterruptedException {
        AppRunner.Output output = app.run("quit");

        assertTrue(output.text().startsWith("Hello, Welcome to StockHolm, your inventory manager program\n"
                + "    Loaded 0 inventory(ies) and 0 order(s).\n"));
        assertTrue(output.text().endsWith("See you next time\n"));
        assertEquals(0, output.exitCode());
        assertFalse(Files.exists(temporaryDirectory.resolve("data")));
    }

    @Test
    public void run_invalidCommands_reportsEachErrorAndKeepsRunning() throws IOException, InterruptedException {
        AppRunner.Output output = app.run("bogus", "inv add", "enter Ghost", "back", "inv add Shop", "inv list",
                "quit");

        assertTrue(output.text().contains("    Unknown command: bogus\n"));
        assertTrue(output.text().contains("    Missing inventory name. Usage: inv add NAME\n"));
        assertTrue(output.text().contains("    No such inventory: Ghost\n"));
        assertTrue(output.text().contains("    Added inventory: Shop\n"));
        assertTrue(output.text().contains("    Inventories:\n    1. Shop\n"));
        assertTrue(output.text().endsWith("See you next time\n"));
        assertEquals(0, output.exitCode());
    }

    @Test
    public void run_orderLifecycle_updatesStockOnlyWhenDelivered() throws IOException, InterruptedException {
        AppRunner.Output output = app.run("inv add Shop", "enter Shop", "import Pen --count=10", "stock",
                "approve 1", "deliver 1", "stock", "export 1 --count=3", "stock", "quit");

        assertTrue(output.text().contains("    Import order created:\n    Item name: Pen\n"));
        assertTrue(output.text().contains("    No items in Shop yet.\n"));
        assertTrue(output.text().contains("    Approved order 1.\n"));
        assertTrue(output.text().contains("    Delivered order 1.\n"));
        assertTrue(output.text().contains("    1. Pen x10\n    Total: 1 item(s), 10 unit(s)\n"));
        assertTrue(output.text().contains("    Export order created:\n"));
        assertTrue(output.text().contains("    1. Pen x7\n    Total: 1 item(s), 7 unit(s)\n"));
    }

    @Test
    public void run_inputEndsWithoutQuit_exitsCleanly() throws IOException, InterruptedException {
        AppRunner.Output output = app.run("inv add Shop");

        assertTrue(output.text().contains("    Added inventory: Shop\n"));
        assertTrue(output.text().endsWith("See you next time\n"));
        assertEquals(0, output.exitCode());
    }
}
