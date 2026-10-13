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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
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
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("OrdersData.csv"));
        Storage storage = new Storage(orderStorage);
        processAndSave(Parser.parseCommand("import Rice --type=Food --count=2.5"), storage);
        assertEquals(1, orderStorage.load().loadedCount());
        assertEquals(OrderState.WAITING_APPROVAL,
                orderStorage.load().inventories().get(0).getOrders().get(0).getState());

        processAndSave(Parser.parseCommand("approve 1"), storage);
        assertEquals(OrderState.WAITING_FOR_DELIVERY,
                orderStorage.load().inventories().get(0).getOrders().get(0).getState());
        processAndSave(Parser.parseCommand("deliver 1"), storage);
        assertEquals(OrderState.DELIVERED, orderStorage.load().inventories().get(0).getOrders().get(0).getState());
        assertEquals(2.5, shop.findItem("Rice").getCount());

        processAndSave(Parser.parseCommand("export 1 1"), storage);
        assertEquals(2, orderStorage.load().loadedCount());
        assertTrue(orderStorage.load().inventories().get(0).getOrders().get(1) instanceof ExportOrder);
        processAndSave(Parser.parseCommand("approve 2"), storage);
        assertEquals(OrderState.WAITING_FOR_DELIVERY,
                orderStorage.load().inventories().get(0).getOrders().get(1).getState());
        processAndSave(Parser.parseCommand("deliver 2"), storage);
        assertEquals(OrderState.DELIVERED, orderStorage.load().inventories().get(0).getOrders().get(1).getState());
        assertEquals(1.5, shop.findItem("Rice").getCount());
    }

    @Test
    public void saveAfterCommand_otherInventoryAndDeletion_preservesOtherOrdersAndRemovesDeletedOrders()
            throws StockHolmException, IOException {
        enterNewShop();
        OrderStorage orderStorage = new OrderStorage(temporaryDirectory.resolve("OrdersData.csv"));
        Storage storage = new Storage(orderStorage);
        processAndSave(Parser.parseCommand("import Pen"), storage);
        Inventory warehouse = new Inventory("Warehouse");
        inventories.add(warehouse);
        state.enterInventory(warehouse);
        processAndSave(Parser.parseCommand("import Box"), storage);
        assertEquals(2, orderStorage.load().loadedCount());
        state.leaveInventory();

        processAndSave(Parser.parseCommand("inv delete Shop"), storage);

        assertEquals(1, orderStorage.load().loadedCount());
        assertEquals("Warehouse", orderStorage.load().inventories().get(0).getName());
        processAndSave(Parser.parseCommand("inv delete Warehouse"), storage);
        assertEquals(0, orderStorage.load().loadedCount());
    }

    @Test
    public void saveAfterCommand_failedCommandAndReadOnlyCommands_doNotWriteFile() throws StockHolmException {
        enterNewShop();
        Path file = temporaryDirectory.resolve("OrdersData.csv");
        OrderStorage orderStorage = new OrderStorage(file);
        Storage storage = new Storage(orderStorage);

        processAndSave(Parser.parseCommand("item add Pen"), storage);
        processAndSave(Parser.parseCommand("order list"), storage);
        assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("approve 1"), storage));
        assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("export 1 2"), storage));

        assertFalse(Files.exists(file));
    }

    @Test
    public void saveAfterCommand_saveFailure_raisesUserFacingExceptionWithCause() throws IOException {
        Inventory shop = enterNewShop();
        Path parent = temporaryDirectory.resolve("data");
        Files.writeString(parent, "cannot create a directory here");
        OrderStorage orderStorage = new OrderStorage(parent.resolve("OrdersData.csv"));
        Storage storage = new Storage(orderStorage);

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("import Pen"), storage));

        assertTrue(e.getMessage().contains("applied in memory"));
        assertTrue(e.getCause() instanceof IOException);
        assertEquals(1, shop.getOrders().size());
        assertEquals("Pen", shop.getOrders().get(0).getItem().getName());
        assertEquals("cannot create a directory here", Files.readString(parent));
    }

    @Test
    public void loadOrders_malformedData_restoresOrdersAndReturnsWarningAndSummary()
            throws IOException, StockHolmException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Stock", "", 8));
        ImportOrder existingOrder = new ImportOrder(new Item("Existing", "", 1), shop);
        shop.addOrder(existingOrder);
        Path file = temporaryDirectory.resolve("OrdersData.csv");
        Files.writeString(file, "Shop,DELIVERED,Pen,,1,,Shop\ninvalid row\n"
                + "Warehouse,WAITING_FOR_DELIVERY,Rice,Food,2.5,Warehouse,\n");

        Result result = new Storage(new OrderStorage(file)).loadOrders(state);

        assertTrue(result.message().contains("line 2: \"invalid row\""));
        assertTrue(result.message().endsWith("Loaded 2 order(s) successfully."));
        assertEquals(2, inventories.size());
        assertSame(shop, inventories.get(0));
        assertSame(shop, state.getCurrentInventory());
        assertEquals(2, shop.getOrders().size());
        assertSame(existingOrder, shop.getOrders().get(0));
        assertEquals(OrderState.DELIVERED, shop.getOrders().get(1).getState());
        assertEquals(1, shop.getItems().size());
        assertEquals(8, shop.getItems().get(0).getCount());
        assertTrue(inventories.get(1).getItems().isEmpty());
    }

    @Test
    public void loadOrders_missingFile_returnsZeroSummaryWithoutCreatingFile() throws StockHolmException {
        Path file = temporaryDirectory.resolve("OrdersData.csv");

        Result result = new Storage(new OrderStorage(file)).loadOrders(state);

        assertEquals(new Result("Loaded 0 order(s) successfully.", false), result);
        assertTrue(inventories.isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    public void loadOrders_readFailure_raisesExceptionWithoutChangingState() {
        enterNewShop();

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> new Storage(new OrderStorage(temporaryDirectory)).loadOrders(state));

        assertTrue(e.getMessage().startsWith("Cannot load orders:"));
        assertTrue(e.getCause() instanceof IOException);
        assertEquals(1, inventories.size());
        assertTrue(inventories.get(0).getOrders().isEmpty());
    }

    @BeforeEach
    public void setUp() {
        state = new AppState();
        inventories = state.getInventories();
    }

    /** Mirrors the main loop: save only after command processing succeeds. */
    private Result processAndSave(Command command, Storage storage) throws StockHolmException {
        Result result = Processor.process(command, state);
        storage.saveAfterCommand(command, state);
        return result;
    }

    /** Creates an inventory named "Shop" and enters it. */
    private Inventory enterNewShop() {
        Inventory shop = new Inventory("Shop");
        inventories.add(shop);
        state.enterInventory(shop);
        return shop;
    }
}
