package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.processor.Result;
import stockholm.state.AppState;

/** Tests application persistence and the process-then-save flow using isolated files. */
class StorageTest {
    private AppState state;
    private ArrayList<Inventory> inventories;

    @TempDir
    private Path temporaryDirectory;

    @Test
    public void saveAfterCommand_orderChanges_savesEachCreationApprovalAndDelivery()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("orders.csv"));
        Storage storage = new Storage(orderStorage, inventoryStorage());
        processAndSave(Parser.parseCommand("import Rice --type=Food --count=2.5"), storage);
        assertEquals(1, orderStorage.load().orders().size());
        assertEquals(OrderState.WAITING_APPROVAL,
                orderStorage.load().orders().get(0).getState());

        processAndSave(Parser.parseCommand("approve 1"), storage);
        assertEquals(OrderState.WAITING_FOR_DELIVERY,
                orderStorage.load().orders().get(0).getState());
        processAndSave(Parser.parseCommand("deliver 1"), storage);
        assertEquals(OrderState.DELIVERED, orderStorage.load().orders().get(0).getState());
        assertEquals(2.5, shop.findItem("Rice").getCount());

        processAndSave(Parser.parseCommand("export 1 --count=1"), storage);
        assertEquals(2, orderStorage.load().orders().size());
        assertTrue(orderStorage.load().orders().get(1) instanceof ExportOrder);
        processAndSave(Parser.parseCommand("approve 2"), storage);
        assertEquals(OrderState.WAITING_FOR_DELIVERY,
                orderStorage.load().orders().get(1).getState());
        processAndSave(Parser.parseCommand("deliver 2"), storage);
        assertEquals(OrderState.DELIVERED, orderStorage.load().orders().get(1).getState());
        assertEquals(1.5, shop.findItem("Rice").getCount());
    }

    @Test
    public void saveAfterCommand_rejectOrder_savesOrdersAndRestoresExportStock()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("orders.csv"));
        InventoryStorage invStorage = inventoryStorage();
        Storage storage = new Storage(orderStorage, invStorage);

        processAndSave(Parser.parseCommand("import Rice --count=2.5"), storage);
        processAndSave(Parser.parseCommand("reject 1 Out of stock"), storage);
        assertEquals(OrderState.REJECTED, orderStorage.load().orders().get(0).getState());
        assertEquals("Out of stock", orderStorage.load().orders().get(0).getReason());

        processAndSave(Parser.parseCommand("item add Pen --count=5"), storage);
        processAndSave(Parser.parseCommand("export 1 --count=2"), storage);
        assertEquals(3.0, shop.findItem("Pen").getCount());

        processAndSave(Parser.parseCommand("reject 2 Cancelled"), storage);
        assertEquals(OrderState.REJECTED, orderStorage.load().orders().get(1).getState());
        assertEquals("Cancelled", orderStorage.load().orders().get(1).getReason());
        assertEquals(5.0, shop.findItem("Pen").getCount());
        assertEquals(5.0, invStorage.load().inventories().get(0).findItem("Pen").getCount());
    }

    @Test
    public void saveAfterCommand_cancelOrder_savesOrdersAndRestoresExportStock()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("orders.csv"));
        InventoryStorage invStorage = inventoryStorage();
        Storage storage = new Storage(orderStorage, invStorage);

        processAndSave(Parser.parseCommand("import Rice --count=2.5"), storage);
        processAndSave(Parser.parseCommand("approve 1"), storage);
        processAndSave(Parser.parseCommand("cancel 1"), storage);
        assertEquals(OrderState.CANCELED, orderStorage.load().orders().get(0).getState());

        processAndSave(Parser.parseCommand("item add Pen --count=5"), storage);
        processAndSave(Parser.parseCommand("export 1 --count=2"), storage);
        assertEquals(3.0, shop.findItem("Pen").getCount());

        processAndSave(Parser.parseCommand("approve 2"), storage);
        processAndSave(Parser.parseCommand("cancel 2"), storage);
        assertEquals(OrderState.CANCELED, orderStorage.load().orders().get(1).getState());
        assertEquals(5.0, shop.findItem("Pen").getCount());
        assertEquals(5.0, invStorage.load().inventories().get(0).findItem("Pen").getCount());
    }

    @Test
    public void saveAfterCommand_otherInventoryAndDeletion_keepsEveryOrderInHistory()
            throws StockHolmException, IOException {
        enterNewShop();
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("orders.csv"));
        Storage storage = new Storage(orderStorage, inventoryStorage());
        processAndSave(Parser.parseCommand("import Pen"), storage);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);
        processAndSave(Parser.parseCommand("import Box"), storage);
        assertEquals(2, orderStorage.load().orders().size());
        state.leaveInventory();

        processAndSave(Parser.parseCommand("inv delete Shop"), storage);
        processAndSave(Parser.parseCommand("inv delete Warehouse"), storage);

        assertEquals(2, orders().size());
        assertEquals(2, orderStorage.load().orders().size());
        assertEquals("Shop", orderStorage.load().orders().get(0).getInventoryName());
        assertEquals("Warehouse", orderStorage.load().orders().get(1).getInventoryName());
    }

    @Test
    public void saveAfterCommand_failedCommandAndReadOnlyCommands_doNotWriteFile() throws StockHolmException {
        enterNewShop();
        Path file = temporaryDirectory.resolve("orders.csv");
        OrderStorage orderStorage = new OrderStorage(file);
        Storage storage = new Storage(orderStorage, inventoryStorage());

        processAndSave(Parser.parseCommand("item add Pen"), storage);
        processAndSave(Parser.parseCommand("order list"), storage);
        assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("approve 1"), storage));
        assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("export 1 --count=2"), storage));

        assertFalse(Files.exists(file));
    }

    @Test
    public void saveAfterCommand_saveFailure_raisesUserFacingExceptionWithCause() throws IOException {
        Inventory shop = enterNewShop();
        Path parent = temporaryDirectory.resolve("data");
        Files.writeString(parent, "cannot create a directory here");
        OrderStorage orderStorage = new OrderStorage(parent.resolve("orders.csv"));
        Storage storage = new Storage(orderStorage, inventoryStorage());

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("import Pen"), storage));

        assertTrue(e.getMessage().contains("applied in memory"));
        assertTrue(e.getCause() instanceof IOException);
        assertEquals(1, orders().size());
        assertEquals("Pen", orders().get(0).getItem().getName());
        assertEquals("cannot create a directory here", Files.readString(parent));
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
    public void saveAfterCommand_inventoryChanges_saveInventoriesOnly() throws StockHolmException, IOException {
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Storage storage = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile));

        processAndSave(Parser.parseCommand("inv add Shop"), storage);
        assertTrue(Files.exists(inventoryFile));
        processAndSave(Parser.parseCommand("enter Shop"), storage);
        processAndSave(Parser.parseCommand("item add Pen --count=3"), storage);

        assertFalse(Files.exists(orderFile));
        InventoryStorage.LoadResult saved = new InventoryStorage(inventoryFile).load();
        assertEquals("Pen x3", saved.inventories().get(0).getItems().get(0).toString());
    }

    @Test
    public void saveAfterCommand_orderOnlyAndReadOnlyCommands_doNotWriteInventories() throws StockHolmException {
        enterNewShop();
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")),
                new InventoryStorage(inventoryFile));

        for (String input : new String[]{"import Pen", "approve 1", "order list", "item list", "stock"}) {
            processAndSave(Parser.parseCommand(input), storage);
        }

        assertFalse(Files.exists(inventoryFile));
    }

    @Test
    public void saveAfterCommand_exportAndDeliver_saveBothFiles() throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        Path inventoryFile = temporaryDirectory.resolve("inventories.json");
        Path orderFile = temporaryDirectory.resolve("orders.csv");
        Storage storage = new Storage(new OrderStorage(orderFile), new InventoryStorage(inventoryFile));

        processAndSave(Parser.parseCommand("export 1 --count=1"), storage);
        assertEquals("Rice (Food) x2",
                new InventoryStorage(inventoryFile).load().inventories().get(0).getItems().get(0).toString());
        assertEquals(1, new OrderStorage(orderFile).load().orders().size());

        Files.delete(inventoryFile);
        processAndSave(Parser.parseCommand("approve 1"), storage);
        processAndSave(Parser.parseCommand("deliver 1"), storage);
        assertTrue(Files.exists(inventoryFile));
        assertEquals(OrderState.DELIVERED,
                new OrderStorage(orderFile).load().orders().get(0).getState());
    }

    @Test
    public void saveAfterCommand_bothSavesFail_reportsBoth() throws IOException {
        enterNewShop();
        Path blocked = temporaryDirectory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        Storage storage = new Storage(new OrderStorage(blocked.resolve("orders.csv")),
                new InventoryStorage(blocked.resolve("inventories.json")));
        state.getCurrentInventory().addItem(new Item("Rice", "", 2));

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("export 1 --count=1"), storage));

        assertTrue(e.getMessage().contains("inventories could not be saved"));
        assertTrue(e.getMessage().contains("orders could not be saved"));
        assertTrue(e.getCause() instanceof IOException);
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

    @BeforeEach
    public void setUp() {
        state = new AppState();
        inventories = state.getInventories();
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

    /** Returns an inventory storage in the temporary folder, so no test touches the real data folder. */
    private InventoryStorage inventoryStorage() {
        return new InventoryStorage(temporaryDirectory.resolve("inventories.json"));
    }

    /** Mirrors the main loop: save only after command processing succeeds. */
    private Result processAndSave(Command command, Storage storage) throws StockHolmException {
        Result result = Processor.process(command, state);
        storage.saveAfterCommand(command, state);
        return result;
    }

    /** Shortcut to every order in {@code state}'s order history. */
    private List<Order> orders() {
        return state.getOrderHistory().getOrders();
    }

    /** Creates an inventory named "Shop" and enters it. */
    private Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }
}
