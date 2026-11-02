package stockholm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests that data typed in one run of the whole app is saved and restored by the next run. */
class PersistenceTest {
    @TempDir
    private Path temporaryDirectory;
    private Path dataDirectory;
    private AppRunner app;

    @BeforeEach
    public void setUp() {
        app = new AppRunner(temporaryDirectory);
        dataDirectory = temporaryDirectory.resolve("data");
    }

    @Test
    public void run_ordersAndTransfers_continueAfterRestarts() throws IOException, InterruptedException {
        app.run("inv add Shop", "inv add Warehouse", "enter Shop", "import Pen --count=10", "approve 1",
                "deliver 1", "export 1 --count=3", "transfer 1 --count=2 --to=Warehouse", "quit");
        assertTrue(Files.exists(dataDirectory.resolve("inventories.json")));
        assertTrue(Files.exists(dataDirectory.resolve("orders.csv")));

        AppRunner.Output second = app.run("order list", "stock Shop", "approve 3", "deliver 3", "quit");
        assertTrue(second.text().contains("    Loaded 2 inventory(ies) and 3 order(s).\n"));
        assertTrue(second.text().contains("    1. Import order (to Shop)\n       State: DELIVERED\n"));
        assertTrue(second.text().contains("    2. Export order (from Shop)\n       State: WAITING_APPROVAL\n"));
        assertTrue(second.text().contains(
                "    3. Transfer order (from Shop to Warehouse)\n       State: WAITING_APPROVAL\n"));
        assertTrue(second.text().contains("    Stock levels in Shop:\n    1. Pen x5\n"));

        AppRunner.Output third = app.run("stock Warehouse", "order list --status=DELIVERED", "quit");
        assertTrue(third.text().contains("    Stock levels in Warehouse:\n    1. Pen x2\n"));
        assertTrue(third.text().contains("Transfer order (from Shop to Warehouse)\n       State: DELIVERED\n"));
    }

    @Test
    public void run_corruptedInventoryFile_backsItUpAndStartsEmpty() throws IOException, InterruptedException {
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("inventories.json"), "{broken");

        AppRunner.Output output = app.run("inv list", "quit");

        assertTrue(output.text().contains("./data/inventories.json is corrupted ("));
        assertTrue(output.text().contains(". Starting with no inventories.\n"));
        assertTrue(output.text().contains("    No inventories yet.\n"));
        List<Path> backups = backupsOf("inventories.json");
        assertEquals(1, backups.size());
        assertEquals("{broken", Files.readString(backups.getFirst()));
    }

    @Test
    public void run_unreadableInventoryFile_stopsBeforeThePrompt() throws IOException, InterruptedException {
        Files.createDirectories(dataDirectory.resolve("inventories.json"));

        AppRunner.Output output = app.run("inv add Shop", "quit");

        assertTrue(output.text().contains("    Cannot load inventories: "));
        assertFalse(output.text().contains("Added inventory"));
        assertFalse(output.text().contains("See you next time"));
        assertEquals(0, output.exitCode());
    }

    @Test
    public void run_quotedNameFromOlderVersion_isCleanedAndReachable() throws IOException, InterruptedException {
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("inventories.json"), "{\"inventories\": [{\"name\": "
                + "\"\\\" New Shop \\\"\", \"items\": [{\"name\": \"Pen\", \"type\": \"\", \"count\": 5}]}]}");

        AppRunner.Output output = app.run("enter New Shop", "item list", "quit");

        assertTrue(output.text().contains("    Renamed inventory 1 in ./data/inventories.json from "
                + "\"\" New Shop \"\" to New Shop"));
        assertTrue(output.text().contains("    Entered inventory: New Shop\n"));
        assertTrue(output.text().contains("    1. Pen x5\n"));
        assertEquals(1, backupsOf("inventories.json").size());
    }

    /** Returns the timestamped backups of a save file in the data folder. */
    private List<Path> backupsOf(String fileName) throws IOException {
        try (Stream<Path> files = Files.list(dataDirectory)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(fileName + ".") && name.endsWith(".bak");
            }).toList();
        }
    }
}
