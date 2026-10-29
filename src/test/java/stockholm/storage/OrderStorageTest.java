package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderHistory;
import stockholm.order.OrderState;
import stockholm.order.TransferOrder;

/** Tests order CSV persistence using isolated files, without touching the application's save file. */
class OrderStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    public void save_transferAllStates_roundTripsEndpointsReasonSnapshotAndMinimum() throws IOException {
        Inventory source = new Inventory("Main, \"Warehouse\"");
        Inventory destination = new Inventory("Shop, East");
        OrderHistory history = new OrderHistory();
        for (OrderState state : OrderState.values()) {
            Item rice = new Item("Rice", "Food", 2.5);
            rice.setMinCount(5);
            history.add(new TransferOrder(rice, source, destination, state,
                    state == OrderState.REJECTED ? "Damaged, \"box\"" : null));
        }
        OrderStorage storage = storage();
        storage.save(history);
        OrderStorage.LoadResult loaded = storage.load();
        assertTrue(loaded.warnings().isEmpty());
        assertEquals(OrderState.values().length, loaded.orders().size());
        for (int i = 0; i < OrderState.values().length; i++) {
            Order order = loaded.orders().get(i);
            assertInstanceOf(TransferOrder.class, order);
            assertEquals(source.getName(), order.getInventoryName());
            assertEquals(source.getName(), order.getSrc());
            assertEquals(destination.getName(), order.getDst());
            assertEquals(OrderState.values()[i], order.getState());
            assertEquals("Rice", order.getItem().getName());
            assertEquals("Food", order.getItem().getType());
            assertEquals(2.5, order.getItem().getCount());
            assertEquals(5.0, order.getItem().getMinCount());
            assertEquals(order.getState() == OrderState.REJECTED ? "Damaged, \"box\"" : null, order.getReason());
        }
        assertTrue(source.getItems().isEmpty());
        assertTrue(destination.getItems().isEmpty());
    }

    @Test
    public void load_invalidTransferEndpoints_skipsRows() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Shop,WAITING_APPROVAL,Rice,Food,2,Shop,Shop",
                "Other,WAITING_APPROVAL,Rice,Food,2,Shop,Warehouse",
                "Shop,WAITING_APPROVAL,Rice,Food,2,Shop,   ",
                "Shop,WAITING_APPROVAL,Rice,Food,2,Shop,Warehouse"));
        OrderStorage.LoadResult loaded = new OrderStorage(file).load();
        assertEquals(3, loaded.warnings().size());
        assertEquals(1, loaded.orders().size());
        assertInstanceOf(TransferOrder.class, loaded.orders().getFirst());
    }

    private OrderStorage storage() {
        return new OrderStorage(temporaryDirectory.resolve("data/orders.csv"));
    }

    private static OrderHistory historyOf(Order... orders) {
        OrderHistory history = new OrderHistory();
        for (Order order : orders) {
            history.add(order);
        }
        return history;
    }

    @Test
    public void load_missingFile_returnsZeroOrdersWithoutCreatingFile() throws IOException {
        OrderStorage.LoadResult loaded = storage().load();

        assertTrue(loaded.orders().isEmpty());
        assertTrue(loaded.warnings().isEmpty());
        assertFalse(Files.exists(temporaryDirectory.resolve("data")));
    }

    @Test
    public void save_multipleInventories_roundTripsOrdersInCreationOrder() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Stock only", "", 99));
        Inventory warehouse = new Inventory("Warehouse");
        OrderHistory history = historyOf(
                new ImportOrder(new Item("Rice", "Food", 2.5), shop),
                new ImportOrder(new Item("Box", "", 4), warehouse),
                new ExportOrder(new Item("Pen", "", 3), shop, OrderState.DELIVERED),
                new ImportOrder(new Item("Rice", "Food", 1), shop, OrderState.WAITING_FOR_DELIVERY));
        OrderStorage storage = storage();

        storage.save(history);
        OrderStorage.LoadResult loaded = storage.load();

        assertTrue(loaded.warnings().isEmpty());
        List<Order> orders = loaded.orders();
        assertEquals(4, orders.size());
        Order first = orders.get(0);
        assertInstanceOf(ImportOrder.class, first);
        assertEquals("Rice", first.getItem().getName());
        assertEquals("Food", first.getItem().getType());
        assertEquals(2.5, first.getItem().getCount());
        assertEquals(OrderState.WAITING_APPROVAL, first.getState());
        assertEquals("Shop", first.getDst());
        assertNull(first.getSrc());
        assertEquals("Warehouse", orders.get(1).getInventoryName());
        Order third = orders.get(2);
        assertInstanceOf(ExportOrder.class, third);
        assertEquals(OrderState.DELIVERED, third.getState());
        assertEquals("Shop", third.getSrc());
        assertNull(third.getDst());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, orders.get(3).getState());
        String csv = Files.readString(temporaryDirectory.resolve("data/orders.csv"));
        assertTrue(csv.startsWith("Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason"));
        assertFalse(csv.contains("Stock only"));
        assertFalse(csv.contains("ImportOrder"));
        assertFalse(csv.contains("ExportOrder"));
    }

    @Test
    public void save_allStates_restoresExactlyWithoutReplayingDelivery() throws IOException {
        Inventory inventory = new Inventory("Shop");
        OrderHistory history = new OrderHistory();
        for (OrderState state : OrderState.values()) {
            history.add(new ImportOrder(new Item("Pen", "", 1), inventory, state));
            history.add(new ExportOrder(new Item("Box", "", 2), inventory, state));
        }
        OrderStorage storage = storage();

        storage.save(history);
        List<Order> restored = storage.load().orders();

        for (int i = 0; i < OrderState.values().length; i++) {
            assertEquals(OrderState.values()[i], restored.get(i * 2).getState());
            assertEquals(OrderState.values()[i], restored.get(i * 2 + 1).getState());
        }
    }

    @Test
    public void save_commasQuotesUnicodeAndSpaces_roundTripsCsvFields() throws IOException {
        Inventory inventory = new Inventory("  仓库, \"Main\"  ");
        OrderStorage storage = storage();

        storage.save(historyOf(new ImportOrder(new Item("\"Rice\", 米", "Food, \"Grain\"", 0.1), inventory),
                new ExportOrder(new Item("Pen", "", 1), inventory)));
        OrderStorage.LoadResult loaded = storage.load();

        assertTrue(loaded.warnings().isEmpty());
        List<Order> restored = loaded.orders();
        assertEquals(2, restored.size());
        assertEquals(inventory.getName(), restored.get(0).getDst());
        assertEquals("\"Rice\", 米", restored.get(0).getItem().getName());
        assertEquals("Food, \"Grain\"", restored.get(0).getItem().getType());
        assertEquals(0.1, restored.get(0).getItem().getCount());
        assertEquals(inventory.getName(), restored.get(1).getSrc());
    }

    @Test
    public void load_malformedRows_reportsPhysicalLinesAndContinuesLoading() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        List<String> lines = List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop",
                "Ghost,UNKNOWN,Pen,,1,,Ghost",
                "Ghost,WAITING_APPROVAL,Pen,,NaN,,Ghost",
                "\"unclosed",
                "Warehouse,DELIVERED,Rice,Food,2.5,Warehouse,",
                "Shop,WAITING_APPROVAL,Pen,,1,,Other");
        Files.write(file, lines, StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(2, loaded.orders().size());
        assertEquals(4, loaded.warnings().size());
        int[] erroneousLines = {3, 4, 5, 7};
        for (int i = 0; i < erroneousLines.length; i++) {
            int lineNumber = erroneousLines[i];
            assertTrue(loaded.warnings().get(i).contains("Skipped order on line " + lineNumber + " in "));
            assertTrue(loaded.warnings().get(i).contains("\"" + lines.get(lineNumber - 1) + "\""));
        }
        assertEquals(lines, Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    public void load_legacyHeader_isSkippedWithoutWarning() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of("Inventory,OrderState,ItemName,ItemType,ItemCount,src,dst",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(1, loaded.orders().size());
        assertTrue(loaded.warnings().isEmpty());
    }

    @Test
    public void load_onlyLegacyFileExists_movesItAndLoadsItsOrders() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Path legacy = temporaryDirectory.resolve("OrdersData.csv");
        List<String> lines = List.of("Inventory,OrderState,ItemName,ItemType,ItemCount,src,dst",
                "Shop,DELIVERED,Pen,,2,,Shop");
        Files.write(legacy, lines, StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file, legacy).load();

        assertEquals(1, loaded.orders().size());
        assertFalse(Files.exists(legacy));
        assertEquals(lines, Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    public void load_bothFilesExist_usesCurrentFileAndLeavesLegacyFile() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Path legacy = temporaryDirectory.resolve("OrdersData.csv");
        Files.write(file, List.of("Shop,WAITING_APPROVAL,Pen,,1,,Shop"), StandardCharsets.UTF_8);
        Files.write(legacy, List.of("Shop,DELIVERED,Box,,2,,Shop", "Shop,DELIVERED,Ink,,3,,Shop"),
                StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file, legacy).load();

        assertEquals(1, loaded.orders().size());
        assertTrue(Files.exists(legacy));
    }

    @Test
    public void load_invalidFields_skipsEveryInvalidRow() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Shop,WAITING_APPROVAL,Pen,,1,,Shop,extra");
        lines.add("Shop,WAITING_APPROVAL,Pen,,1,,Shop,,extra");
        lines.add("Shop,WAITING_APPROVAL,Pen,,1");
        lines.add(",WAITING_APPROVAL,Pen,,1,,Shop");
        lines.add("Shop,WAITING_APPROVAL, ,,1,,Shop");
        for (String count : List.of("abc", "0", "-1", "Infinity", "1e999", "1e-999")) {
            lines.add("Shop,WAITING_APPROVAL,Pen,," + count + ",,Shop");
        }
        lines.add("Shop,WAITING_APPROVAL,Pen,,1,,");
        lines.add("Shop,WAITING_APPROVAL,Pen,,1,Shop,Shop");
        lines.add("Shop,WAITING_APPROVAL,Pen,,1,Other,");
        lines.add("Shop,WAITING_APPROVAL,Pe\"n,,1,,Shop");
        lines.add("Shop,WAITING_APPROVAL,\"Pen\"junk,,1,,Shop");
        Files.write(file, lines, StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertTrue(loaded.orders().isEmpty());
        assertEquals(lines.size(), loaded.warnings().size());
    }

    @Test
    public void save_emptyOrders_replacesOldRowsWithHeader() throws IOException {
        OrderStorage storage = storage();
        storage.save(historyOf(new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"))));

        storage.save(new OrderHistory());

        assertTrue(storage.load().orders().isEmpty());
        assertEquals(1, Files.readAllLines(temporaryDirectory.resolve("data/orders.csv")).size());
    }

    @Test
    public void save_invalidOrder_preservesPreviouslySavedData() throws IOException {
        Inventory shop = new Inventory("Shop");
        OrderHistory history = historyOf(new ImportOrder(new Item("Pen", "", 1), shop));
        OrderStorage storage = storage();
        storage.save(history);
        Path file = temporaryDirectory.resolve("data/orders.csv");
        String original = Files.readString(file);

        for (double count : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            OrderHistory invalid = historyOf(new ImportOrder(new Item("Rice", "", count), shop));
            assertThrows(IOException.class, () -> storage.save(invalid));
            assertEquals(original, Files.readString(file));
        }
        history.add(new ImportOrder(new Item("Line\nbreak", "", 1), shop));
        assertThrows(IOException.class, () -> storage.save(history));
        assertEquals(original, Files.readString(file));
    }

    @Test
    public void save_invalidRouteOrUnsupportedOrder_throwsIOException() {
        Inventory shop = new Inventory("Shop");
        ImportOrder invalid = new ImportOrder(new Item("Pen", "", 1), shop);
        invalid.setSrc("Warehouse");
        assertThrows(IOException.class, () -> storage().save(historyOf(invalid)));

        OrderHistory unsupported = historyOf(new Order(new Item("Pen", "", 1)));
        assertThrows(IOException.class, () -> storage().save(unsupported));
    }

    @Test
    public void load_pathIsDirectory_throwsIOException() {
        assertThrows(IOException.class, () -> new OrderStorage(temporaryDirectory).load());
    }

    @Test
    public void save_parentIsFile_throwsIOExceptionAndPreservesParent() throws IOException {
        Path parent = temporaryDirectory.resolve("data");
        Files.writeString(parent, "existing data");

        assertThrows(IOException.class, () -> storage().save(new OrderHistory()));

        assertEquals("existing data", Files.readString(parent));
    }

    @Test
    public void save_targetIsNonEmptyDirectory_throwsIOExceptionAndCleansTemporaryFile() throws IOException {
        Path target = temporaryDirectory.resolve("orders.csv");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "keep");

        assertThrows(IOException.class, () -> new OrderStorage(target).save(new OrderHistory()));

        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        try (var siblings = Files.list(temporaryDirectory)) {
            assertEquals(List.of(target), siblings.toList());
        }
    }

    @Test
    public void save_rejectedOrderWithReason_roundTripsReason() throws IOException {
        OrderHistory history = historyOf(
                new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"), OrderState.REJECTED,
                        "Damaged packaging"),
                new ExportOrder(new Item("Rice", "Food", 2.5), new Inventory("Warehouse"), OrderState.REJECTED,
                        "Out of stock, \"supplier\" delay"));
        OrderStorage storage = storage();

        storage.save(history);
        String csv = Files.readString(temporaryDirectory.resolve("data/orders.csv"));
        assertTrue(csv.contains("Shop,REJECTED,Pen,,1.0,,Shop,Damaged packaging"));
        assertTrue(csv.contains(
                "Warehouse,REJECTED,Rice,Food,2.5,Warehouse,,\"Out of stock, \"\"supplier\"\" delay\""));

        OrderStorage.LoadResult loaded = storage.load();
        assertEquals(2, loaded.orders().size());
        assertEquals("Damaged packaging", loaded.orders().get(0).getReason());
        assertEquals("Out of stock, \"supplier\" delay", loaded.orders().get(1).getReason());
    }

    @Test
    public void load_rejectedOrderWithReason_restoresReason() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason",
                "Shop,REJECTED,Pen,,1,,Shop,Damaged goods"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(1, loaded.orders().size());
        assertTrue(loaded.warnings().isEmpty());
        Order order = loaded.orders().get(0);
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
    }

    @Test
    public void save_itemMinimum_roundTripsAndIsEmptyWhenUnset() throws IOException {
        Inventory shop = new Inventory("Shop");
        Item withMin = new Item("Rice", "Food", 2);
        withMin.setMinCount(5);
        OrderStorage storage = storage();

        storage.save(historyOf(new ExportOrder(withMin, shop), new ImportOrder(new Item("Pen", "", 1), shop)));

        assertEquals(List.of("Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason,ItemMin",
                "Shop,WAITING_APPROVAL,Rice,Food,2.0,Shop,,,5.0",
                "Shop,WAITING_APPROVAL,Pen,,1.0,,Shop,,"),
                Files.readAllLines(temporaryDirectory.resolve("data/orders.csv")));
        List<Order> loaded = storage.load().orders();
        assertEquals(5.0, loaded.get(0).getItem().getMinCount());
        assertEquals(0.0, loaded.get(1).getItem().getMinCount());
    }

    @Test
    public void load_preMinimumEightFieldRows_loadWithoutMinimum() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason",
                "Shop,WAITING_APPROVAL,Rice,Food,2,Shop,,"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertTrue(loaded.warnings().isEmpty());
        assertEquals(0.0, loaded.orders().get(0).getItem().getMinCount());
    }

    @Test
    public void load_invalidItemMinimum_warnsAndSkipsOrder() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        List<String> lines = new ArrayList<>();
        for (String min : List.of("abc", "-1", "NaN", "Infinity")) {
            lines.add("Shop,WAITING_APPROVAL,Rice,Food,2,Shop,,," + min);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertTrue(loaded.orders().isEmpty());
        assertEquals(lines.size(), loaded.warnings().size());
    }

    @Test
    public void load_legacySevenFieldRows_loadsWithNullReason() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst",
                "Shop,REJECTED,Pen,,1,,Shop"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(1, loaded.orders().size());
        assertTrue(loaded.warnings().isEmpty());
        Order order = loaded.orders().get(0);
        assertEquals(OrderState.REJECTED, order.getState());
        assertNull(order.getReason());
    }

    @Test
    public void load_nonRejectedOrderWithReason_warnsAndSkipsOrder() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,SomeReason"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(0, loaded.orders().size());
        assertEquals(1, loaded.warnings().size());
        assertTrue(loaded.warnings().get(0).contains("Only rejected orders can have a reason."));
    }

    @Test
    public void save_rejectedOrderWithLineBreakInReason_throwsIOException() {
        OrderHistory history = historyOf(
                new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"), OrderState.REJECTED, "Line\nbreak"));
        assertThrows(IOException.class, () -> storage().save(history));
    }
}
