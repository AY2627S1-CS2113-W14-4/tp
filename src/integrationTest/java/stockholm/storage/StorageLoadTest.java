package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ImportOrder;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.processor.Result;
import stockholm.state.AppState;

/**
 * Tests {@link Storage#load} on existing, missing, corrupted or malformed save files.
 */
class StorageLoadTest extends StorageTestBase {
    @Test
    public void load_exportOfRemovedItem_restoresItsMinimumOnRejectAfterRestart()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 2);
        rice.setMinCount(5);
        shop.addItem(rice);
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("export 1"), storage);

        state = new AppState();
        inventories = state.getInventories();
        storage.load(state);
        state.enterInventory(state.findInventory("Shop"));
        processAndSave(Parser.parseCommand("reject 1 Damaged"), storage);

        Item restored = state.findInventory("Shop").findItem("Rice");
        assertEquals(2.0, restored.getCount());
        assertEquals(5.0, restored.getMinCount());
    }

    @Test
    public void load_orders_appendsToHistoryWithoutCreatingInventoriesOrChangingStock()
            throws IOException, StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Stock", "", 8));
        ImportOrder existingOrder = new ImportOrder(new Item("Existing", "", 1), shop);
        state.getOrderHistory().add(existingOrder);
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.writeString(file, "Shop,DELIVERED,Pen,,1,,Shop\ninvalid row\n"
                + "Warehouse,WAITING_FOR_DELIVERY,Rice,Food,2.5,Warehouse,\n");

        Result result = new Storage(new OrderStorage(file), inventoryStorage()).load(state);

        assertTrue(result.message().contains("Skipped order on line 2 in " + file + ": \"invalid row\""));
        assertTrue(result.message().endsWith("Loaded 0 inventory(ies) and 2 order(s)."));
        assertEquals(List.of(shop), inventories);
        assertSame(shop, state.getCurrentInventory());
        assertEquals(3, orders().size());
        assertSame(existingOrder, orders().get(0));
        assertEquals(OrderState.DELIVERED, orders().get(1).getState());
        assertEquals("Warehouse", orders().get(2).getInventoryName());
        assertEquals(1, shop.getItems().size());
        assertEquals(8, shop.getItems().get(0).getCount());
    }

    @Test
    public void load_unreadableOrderFile_raisesExceptionWithoutChangingState() {
        enterNewShop();

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> new Storage(new OrderStorage(temporaryDirectory), inventoryStorage()).load(state));

        assertTrue(e.getMessage().startsWith("Cannot load orders:"));
        assertTrue(e.getCause() instanceof IOException);
        assertEquals(1, inventories.size());
        assertTrue(orders().isEmpty());
    }

    @Test
    public void load_savedInventoriesAndOrders_restoresBoth()
            throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": [{\"name\": \"Shop\", \"items\": "
                + "[{\"name\": \"Pen\", \"type\": \"\", \"count\": 3}]}]}");
        Files.writeString(orderFile, "Shop,WAITING_FOR_DELIVERY,Ink,,5,,Shop\n");

        Result result = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile)).load(state);

        assertEquals(new Result("Loaded 1 inventory(ies) and 1 order(s).", false), result);
        assertEquals(1, inventories.size());
        Inventory shop = inventories.get(0);
        assertEquals("Pen x3", shop.getItems().get(0).toString());
        assertEquals(1, orders().size());
        assertEquals("Ink", orders().get(0).getItem().getName());
        assertTrue(backupsOf("inventories.json").isEmpty());
        assertTrue(backupsOf("orders.csv").isEmpty());
    }

    @Test
    public void load_corruptInventoryFile_backsUpAndStillLoadsOrders() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{broken");
        Files.writeString(orderFile, "Shop,WAITING_APPROVAL,Ink,,5,,Shop\n");

        Result result = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile)).load(state);

        assertTrue(result.message().contains("is corrupted"));
        assertTrue(result.message().contains("Starting with no inventories."));
        assertTrue(result.message().endsWith("Loaded 0 inventory(ies) and 1 order(s)."));
        assertEquals(1, backupsOf("inventories.json").size());
        assertEquals("{broken", Files.readString(backupsOf("inventories.json").get(0)));
        assertTrue(inventories.isEmpty());
        assertEquals(1, orders().size());
    }

    @Test
    public void load_skippedEntries_warnsAndBacksUp() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        String content = "{\"inventories\": [{\"name\": \"Shop\", \"items\": "
                + "[{\"name\": \"Pen\", \"count\": -1}, {\"name\": \"Box\", \"count\": 2}]}]}";
        Files.writeString(inventoryFile, content);

        Result result = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")),
                new InventoryStorage(inventoryFile)).load(state);

        assertTrue(result.message().contains("Skipped item 1 of Shop in " + inventoryFile + ": "));
        assertTrue(result.message().contains("Original file copied to"));
        assertEquals(1, backupsOf("inventories.json").size());
        assertEquals(content, Files.readString(backupsOf("inventories.json").get(0)));
        assertEquals("Box x2", inventories.get(0).getItems().get(0).toString());
    }

    @Test
    public void load_quotedOrPaddedNames_cleansNamesWarnsAndBacksUp() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": [{\"name\": \"\\\" New Shop \\\"\", \"items\": "
                + "[{\"name\": \"Pen\", \"count\": 5}]}, {\"name\": \"  Padded  \", \"items\": []}]}");
        Files.writeString(orderFile, "\"\"\" New Shop \"\"\",WAITING_APPROVAL,Pen,,1,\"\"\" New Shop \"\"\","
                + "  Padded  \n");

        Result result = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile)).load(state);

        assertTrue(result.message().contains("Renamed inventory 1 in " + inventoryFile
                + " from \"\" New Shop \"\" to New Shop"));
        assertTrue(result.message().contains("Renamed inventory 2 in " + inventoryFile
                + " from \"  Padded  \" to Padded"));
        assertTrue(result.message().contains("Updated orders in " + orderFile
                + " from inventory \"\" New Shop \"\" to New Shop."));
        assertTrue(result.message().endsWith("Loaded 2 inventory(ies) and 1 order(s)."));
        assertEquals(1, backupsOf("inventories.json").size());
        assertEquals(1, backupsOf("orders.csv").size());
        assertEquals("New Shop", orders().getFirst().getSrc());
        assertEquals("Padded", orders().getFirst().getDst());
        Processor.process(Parser.parseCommand("enter \" New Shop \""), state);
        assertEquals("Pen x5", state.getCurrentInventory().getItems().getFirst().toString());
        state.leaveInventory();
        Processor.process(Parser.parseCommand("enter Padded"), state);
        assertSame(inventories.get(1), state.getCurrentInventory());
    }

    @Test
    public void load_namesEqualAfterCleaning_numbersLaterOneAndKeepsItsOrders()
            throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": ["
                + "{\"name\": \"Shop\", \"items\": [{\"name\": \"Pen\", \"count\": 10}]}, "
                + "{\"name\": \" Shop \", \"items\": [{\"name\": \"Pen\", \"count\": 3}]}]}");
        // The second shop reserved 2 more pens for a pending export.
        Files.writeString(orderFile, " Shop ,WAITING_APPROVAL,Pen,,2, Shop ,\n");

        Result result = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile)).load(state);

        assertTrue(result.message().contains("Renamed inventory 2 in " + inventoryFile
                + " from \" Shop \" to Shop (2), as another inventory is already named Shop."));
        assertTrue(result.message().contains("Updated orders in " + orderFile
                + " from inventory \" Shop \" to Shop (2)."));
        assertEquals("Shop", inventories.get(0).getName());
        assertEquals("Shop (2)", inventories.get(1).getName());
        assertEquals("Shop (2)", orders().getFirst().getSrc());
        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);
        assertEquals(10, inventories.get(0).findItem("Pen").getCount());
        assertEquals(5, inventories.get(1).findItem("Pen").getCount());
    }

    @Test
    public void load_renamedInventoryInReadOnlyFolder_reportsFailedBackupAndSaveAndStillLoads()
            throws StockHolmException, IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "a read-only folder needs POSIX permissions");
        Path dataDirectory = temporaryDirectory.resolve("data");
        Path inventoryFile = dataDirectory.resolve("inventories.json");
        Files.createDirectories(dataDirectory);
        Files.writeString(inventoryFile, "{\"inventories\": [{\"name\": \" Shop \", \"items\": []}]}");
        Storage storage = new Storage(new OrderStorage(dataDirectory.resolve("orders.csv")),
                new InventoryStorage(inventoryFile));

        Result result;
        Files.setPosixFilePermissions(dataDirectory, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(dataDirectory), "the folder is still writable, e.g. when run as root");
            result = storage.load(state);
        } finally {
            Files.setPosixFilePermissions(dataDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }

        assertTrue(result.message().contains("Could not back up " + inventoryFile + ": "));
        assertTrue(result.message().contains("Inventories were renamed, but inventories could not be saved: "));
        assertTrue(result.message().contains("; orders could not be saved: "));
        assertTrue(result.message().endsWith("Loaded 1 inventory(ies) and 0 order(s)."));
        assertEquals("Shop", inventories.getFirst().getName());
        assertTrue(Files.readString(inventoryFile).contains("\" Shop \""), "the file is left as it was");
    }

    @Test
    public void load_renamedInventory_savesBothFilesSoOrdersKeepFollowingIt() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": ["
                + "{\"name\": \"Shop\", \"items\": [{\"name\": \"Pen\", \"count\": 10}]}, "
                + "{\"name\": \" Shop \", \"items\": [{\"name\": \"Pen\", \"count\": 3}]}]}");
        Files.writeString(orderFile, " Shop ,WAITING_APPROVAL,Pen,,2, Shop ,\n");
        Storage storage = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile));

        Result result = storage.load(state);
        assertTrue(result.message().contains("Saved the new inventory names to both save files."));
        assertTrue(Files.readString(orderFile).contains("Shop (2),WAITING_APPROVAL,Pen,,2.0,Shop (2),"));
        // A command that saves only the inventories must not leave the orders naming the old inventory.
        processAndSave(Parser.parseCommand("inv add Warehouse"), storage);

        state = new AppState();
        inventories = state.getInventories();
        result = storage.load(state);
        assertFalse(result.message().contains("Renamed inventory"));
        assertEquals("Shop (2)", orders().getFirst().getSrc());
        Processor.process(Parser.parseCommand("reject 1 Damaged"), state);
        assertEquals(10, state.findInventory("Shop").findItem("Pen").getCount());
        assertEquals(5, state.findInventory("Shop (2)").findItem("Pen").getCount());
    }

    @Test
    public void load_exactDuplicateAndMissingNames_savesNewNamesAtOnce() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": [{\"name\": \"Shop\", \"items\": []}, "
                + "{\"name\": \"Shop\", \"items\": [{\"name\": \"Pen\", \"count\": 3}]}, {\"items\": []}]}");
        Files.writeString(orderFile, "Shop,WAITING_APPROVAL,Pen,,2,Shop,\n");
        Storage storage = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile));

        Result result = storage.load(state);

        assertTrue(result.message().contains("Saved the new inventory names to both save files."));
        String saved = Files.readString(inventoryFile);
        assertTrue(saved.contains("\"name\": \"Shop (2)\"") && saved.contains("\"name\": \"_\""));
        // Identical saved names cannot be told apart, so the order stays with the first Shop.
        assertTrue(Files.readString(orderFile).contains("\nShop,WAITING_APPROVAL,Pen,,2.0,Shop,"));
        state = new AppState();
        assertFalse(storage.load(state).message().contains("Renamed inventory"));
        assertEquals(List.of("Shop", "Shop (2)", "_"),
                state.getInventories().stream().map(Inventory::getName).toList());
    }

    @Test
    public void load_orderNamesCleanedWithoutRenamedInventory_savesOrdersAtOnce()
            throws StockHolmException, IOException {
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(orderFile, " Old ,CANCELED,Pen,,2, Old ,\n");
        Storage storage = new Storage(new OrderStorage(orderFile), inventoryStorage());

        Result result = storage.load(state);

        assertTrue(result.message().contains("Saved the new inventory names to both save files."));
        assertTrue(Files.readString(orderFile).contains("\nOld,CANCELED,Pen,,2.0,Old,"));
        state = new AppState();
        assertFalse(storage.load(state).message().contains("Updated orders"));
    }

    @Test
    public void load_blankNames_becomeNumberedUnderscoresAndKeepTheirOrders()
            throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Files.writeString(inventoryFile, "{\"inventories\": [{\"name\": \"\\\"\\\"\", \"items\": []}, "
                + "{\"name\": \"  \", \"items\": []}, {\"items\": []}]}");
        Files.writeString(orderFile, "\"\"\"\"\"\",WAITING_APPROVAL,Pen,,1,,\"\"\"\"\"\"\n"
                + "  ,WAITING_APPROVAL,Ink,,1,,  \n");

        Result result = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile)).load(state);

        assertTrue(result.message().contains("Renamed inventory 1 in " + inventoryFile
                + " from \"\"\"\" to _, as inventory names cannot be blank."));
        assertTrue(result.message().contains("Renamed inventory 3 in " + inventoryFile
                + " from no name to _ (3), as another inventory is already named _."));
        assertEquals(List.of("_", "_ (2)", "_ (3)"), inventories.stream().map(Inventory::getName).toList());
        assertEquals("_", orders().get(0).getDst());
        assertEquals("_ (2)", orders().get(1).getDst());
        Processor.process(Parser.parseCommand("enter _ (2)"), state);
        assertSame(inventories.get(1), state.getCurrentInventory());
    }

    @Test
    public void load_malformedOrderRow_warnsAndBacksUpOrderFile() throws StockHolmException, IOException {
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        String content = "Shop,WAITING_APPROVAL,Ink,,5,,Shop\ninvalid row\n";
        Files.writeString(orderFile, content);

        Result result = new Storage(new OrderStorage(orderFile), inventoryStorage()).load(state);

        assertTrue(result.message().contains("Skipped order on line 2 in " + orderFile + ": \"invalid row\""));
        assertTrue(result.message().contains("Original file copied to"));
        assertEquals(1, backupsOf("orders.csv").size());
        assertEquals(content, Files.readString(backupsOf("orders.csv").get(0)));
        assertEquals(1, orders().size());
        assertTrue(inventories.isEmpty());
    }

    @Test
    public void load_sameBadFileTwice_keepsOneBackup() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Files.writeString(inventoryFile, "{broken");
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")),
                new InventoryStorage(inventoryFile));

        storage.load(new AppState());
        storage.load(new AppState());
        assertEquals(1, backupsOf("inventories.json").size());

        Files.writeString(inventoryFile, "{broken differently");
        storage.load(new AppState());
        assertEquals(2, backupsOf("inventories.json").size());
    }

    @Test
    public void load_unreadableInventoryFile_raisesException() throws IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Files.createDirectories(inventoryFile);
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")),
                new InventoryStorage(inventoryFile));

        StockHolmException e = assertThrows(StockHolmException.class, () -> storage.load(state));

        assertTrue(e.getMessage().startsWith("Cannot load inventories:"));
        assertTrue(inventories.isEmpty());
    }

    @Test
    public void load_noSaveFiles_loadsNothingWithoutCreatingFiles() throws StockHolmException {
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Storage storage = new Storage(new OrderStorage(orderFile), inventoryStorage());

        assertEquals(new Result("Loaded 0 inventory(ies) and 0 order(s).", false), storage.load(state));
        assertTrue(inventories.isEmpty());
        assertFalse(Files.exists(orderFile));
        assertFalse(Files.exists(temporaryDirectory.resolve("inventories.json")));
    }

    /** Returns the timestamped backups of a save file in the temporary folder, sorted by name. */
    private List<Path> backupsOf(String fileName) throws IOException {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(fileName + ".") && name.endsWith(".bak");
            }).sorted().toList();
        }
    }
}
