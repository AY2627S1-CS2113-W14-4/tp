package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderState;
import stockholm.order.TransferOrder;
import stockholm.parser.Parser;
import stockholm.processor.Processor;
import stockholm.state.AppState;

/**
 * Tests the process-then-save flow of {@link Storage#saveAfterCommand}, including
 * restoring the saved data after a restart.
 */
class StorageSaveTest extends StorageTestBase {
    @Test
    public void saveAfterCommand_addQuotedName_storesNormalizedNameAcrossRestart() throws StockHolmException {
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("inv add \"  New Shop  \""), storage);

        state = new AppState();
        storage.load(state);
        assertEquals("New Shop", state.getInventories().getFirst().getName());
        Processor.process(Parser.parseCommand("enter New Shop"), state);
        assertSame(state.getInventories().getFirst(), state.getCurrentInventory());
        Processor.process(Parser.parseCommand("enter \" New Shop \""), state);
        assertSame(state.getInventories().getFirst(), state.getCurrentInventory());
    }

    @Test
    public void saveAfterCommand_renameQuotedNewName_canEnterAndDeleteAfterRestart() throws StockHolmException {
        Inventory shop = enterNewShop();
        state.getOrderHistory().add(new ImportOrder(new Item("Pen", "", 1), shop));
        state.leaveInventory();
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("inv rename Shop \" New Shop \""), storage);

        state = new AppState();
        storage.load(state);
        assertEquals("New Shop", state.getInventories().getFirst().getName());
        assertEquals("New Shop", orders().getFirst().getDst());
        Processor.process(Parser.parseCommand("enter \" New Shop \""), state);
        assertEquals("New Shop", state.getCurrentInventory().getName());
        state.leaveInventory();
        processAndSave(Parser.parseCommand("inv delete \" New Shop \""), storage);

        state = new AppState();
        storage.load(state);
        assertNull(state.findInventory("New Shop"));
        assertEquals(OrderState.CANCELED, orders().getFirst().getState());
    }

    @Test
    public void saveAfterCommand_renameAndRestart_restoresInventoryAndOrderEndpoints() throws StockHolmException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 3);
        rice.setMinCount(5);
        shop.addItem(rice);
        inventories.add(new Inventory("Warehouse"));
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("transfer 1 --count=1 --to=Warehouse"), storage);
        state.leaveInventory();
        processAndSave(Parser.parseCommand("inv rename Shop Central Shop"), storage);

        state = new AppState();
        storage.load(state);
        assertEquals(2.0, state.findInventory("Central Shop").findItem("Rice").getCount());
        assertEquals(5.0, state.findInventory("Central Shop").findItem("Rice").getMinCount());
        assertEquals("Central Shop", orders().getFirst().getSrc());
        assertEquals("Warehouse", orders().getFirst().getDst());
        processAndSave(Parser.parseCommand("approve 1"), storage);
        processAndSave(Parser.parseCommand("deliver 1"), storage);
        assertEquals(1.0, state.findInventory("Warehouse").findItem("Rice").getCount());
    }

    @Test
    public void saveAfterCommand_transferAndRestart_resumesWithoutReplayingStockChanges()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        Item rice = new Item("Rice", "Food", 3);
        rice.setMinCount(5);
        shop.addItem(rice);
        inventories.add(new Inventory("Warehouse"));
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("transfer 1 --count=2 --to=Warehouse"), storage);

        state = new AppState();
        storage.load(state);
        assertEquals(1.0, state.findInventory("Shop").findItem("Rice").getCount());
        assertTrue(state.findInventory("Warehouse").getItems().isEmpty());
        assertTrue(state.getOrderHistory().getOrders().getFirst() instanceof TransferOrder);
        processAndSave(Parser.parseCommand("approve 1"), storage);
        processAndSave(Parser.parseCommand("deliver 1"), storage);

        state = new AppState();
        storage.load(state);
        assertEquals(1.0, state.findInventory("Shop").findItem("Rice").getCount());
        assertEquals(2.0, state.findInventory("Warehouse").findItem("Rice").getCount());
        assertEquals(0.0, state.findInventory("Warehouse").findItem("Rice").getMinCount());
        assertEquals(OrderState.DELIVERED, state.getOrderHistory().getOrders().getFirst().getState());
    }

    @Test
    public void saveAfterCommand_transferRefundAfterRestart_restoresSourceMinimum()
            throws StockHolmException, IOException {
        for (boolean cancel : new boolean[]{false, true}) {
            state = new AppState();
            inventories = state.getInventories();
            Inventory shop = enterNewShop();
            Item rice = new Item("Rice", "Food", 3);
            rice.setMinCount(5);
            shop.addItem(rice);
            inventories.add(new Inventory("Warehouse"));
            Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")),
                    inventoryStorage());
            processAndSave(Parser.parseCommand("transfer 1 --to=Warehouse"), storage);
            if (cancel) {
                processAndSave(Parser.parseCommand("approve 1"), storage);
            }
            state = new AppState();
            storage.load(state);
            processAndSave(Parser.parseCommand(cancel ? "cancel 1" : "reject 1 Damaged"), storage);
            state = new AppState();
            storage.load(state);
            assertEquals(3.0, state.findInventory("Shop").findItem("Rice").getCount());
            assertEquals(5.0, state.findInventory("Shop").findItem("Rice").getMinCount());
            assertTrue(state.findInventory("Warehouse").getItems().isEmpty());
            Order order = state.getOrderHistory().getOrders().getFirst();
            assertEquals(cancel ? OrderState.CANCELED : OrderState.REJECTED, order.getState());
        }
    }

    @Test
    public void saveAfterCommand_deleteTransferEndpoint_closesOrderAcrossRestart()
            throws StockHolmException, IOException {
        Inventory shop = enterNewShop();
        shop.addItem(new Item("Rice", "Food", 3));
        inventories.add(new Inventory("Warehouse"));
        Storage storage = new Storage(new OrderStorage(temporaryDirectory.resolve("orders.csv")), inventoryStorage());
        processAndSave(Parser.parseCommand("transfer 1 --to=Warehouse"), storage);
        processAndSave(Parser.parseCommand("approve 1"), storage);
        state.leaveInventory();
        processAndSave(Parser.parseCommand("inv delete Warehouse"), storage);
        processAndSave(Parser.parseCommand("inv add Warehouse"), storage);
        state = new AppState();
        storage.load(state);
        assertEquals(OrderState.CANCELED, state.getOrderHistory().getOrders().getFirst().getState());
        assertEquals(3.0, state.findInventory("Shop").findItem("Rice").getCount());
        assertThrows(StockHolmException.class,
                () -> processAndSave(Parser.parseCommand("deliver 1"), storage));
        assertTrue(state.findInventory("Warehouse").getItems().isEmpty());
    }

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
    public void saveAfterCommand_inventoryDeletion_savesItsOrdersAsCanceled()
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

        List<Order> saved = orderStorage.load().orders();
        assertEquals(2, saved.size());
        assertEquals("Shop", saved.get(0).getInventoryName());
        assertEquals(OrderState.CANCELED, saved.get(0).getState());
        assertEquals("Warehouse", saved.get(1).getInventoryName());
        assertEquals(OrderState.CANCELED, saved.get(1).getState());
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
}
