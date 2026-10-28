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
import stockholm.order.OrderState;

/** Tests order CSV persistence using isolated files, without touching the application's save file. */
class OrderStorageTest {
    @TempDir
    private Path temporaryDirectory;

    private OrderStorage storage() {
        return new OrderStorage(temporaryDirectory.resolve("data/orders.csv"));
    }

    @Test
    public void load_missingFile_returnsZeroOrdersWithoutCreatingFile() throws IOException {
        OrderStorage.LoadResult loaded = storage().load();

        assertEquals(0, loaded.loadedCount());
        assertTrue(loaded.inventories().isEmpty());
        assertTrue(loaded.warnings().isEmpty());
        assertFalse(Files.exists(temporaryDirectory.resolve("data")));
    }

    @Test
    public void save_multipleInventories_roundTripsOrdersOnlyInCreationOrder() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addItem(new Item("Stock only", "", 99));
        shop.addOrder(new ImportOrder(new Item("Rice", "Food", 2.5), shop));
        shop.addOrder(new ExportOrder(new Item("Pen", "", 3), shop, OrderState.DELIVERED));
        shop.addOrder(new ImportOrder(new Item("Rice", "Food", 1), shop, OrderState.WAITING_FOR_DELIVERY));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addOrder(new ImportOrder(new Item("Box", "", 4), warehouse));
        OrderStorage storage = storage();

        storage.save(List.of(shop, warehouse, new Inventory("Empty")));
        OrderStorage.LoadResult loaded = storage.load();

        assertEquals(4, loaded.loadedCount());
        assertEquals(2, loaded.inventories().size());
        assertTrue(loaded.warnings().isEmpty());
        Inventory restoredShop = loaded.inventories().get(0);
        assertEquals("Shop", restoredShop.getName());
        assertTrue(restoredShop.getItems().isEmpty());
        assertEquals(3, restoredShop.getOrders().size());
        Order first = restoredShop.getOrders().get(0);
        assertInstanceOf(ImportOrder.class, first);
        assertEquals("Rice", first.getItem().getName());
        assertEquals("Food", first.getItem().getType());
        assertEquals(2.5, first.getItem().getCount());
        assertEquals(OrderState.WAITING_APPROVAL, first.getState());
        assertEquals("Shop", first.getDst());
        assertNull(first.getSrc());
        Order second = restoredShop.getOrders().get(1);
        assertInstanceOf(ExportOrder.class, second);
        assertEquals(OrderState.DELIVERED, second.getState());
        assertEquals("Shop", second.getSrc());
        assertNull(second.getDst());
        assertEquals(OrderState.WAITING_FOR_DELIVERY, restoredShop.getOrders().get(2).getState());
        assertEquals("Warehouse", loaded.inventories().get(1).getName());
        assertEquals(1, loaded.inventories().get(1).getOrders().size());
        String csv = Files.readString(temporaryDirectory.resolve("data/orders.csv"));
        assertTrue(csv.startsWith("Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason"));
        assertFalse(csv.contains("Stock only"));
        assertFalse(csv.contains("ImportOrder"));
        assertFalse(csv.contains("ExportOrder"));
    }

    @Test
    public void save_allStates_restoresExactlyWithoutReplayingDelivery() throws IOException {
        Inventory inventory = new Inventory("Shop");
        for (OrderState state : OrderState.values()) {
            inventory.addOrder(new ImportOrder(new Item("Pen", "", 1), inventory, state));
            inventory.addOrder(new ExportOrder(new Item("Box", "", 2), inventory, state));
        }
        OrderStorage storage = storage();

        storage.save(List.of(inventory));
        Inventory restored = storage.load().inventories().get(0);

        assertTrue(restored.getItems().isEmpty());
        for (int i = 0; i < OrderState.values().length; i++) {
            assertEquals(OrderState.values()[i], restored.getOrders().get(i * 2).getState());
            assertEquals(OrderState.values()[i], restored.getOrders().get(i * 2 + 1).getState());
        }
    }

    @Test
    public void save_commasQuotesUnicodeAndSpaces_roundTripsCsvFields() throws IOException {
        Inventory inventory = new Inventory("  仓库, \"Main\"  ");
        inventory.addOrder(new ImportOrder(new Item("\"Rice\", 米", "Food, \"Grain\"", 0.1), inventory));
        inventory.addOrder(new ExportOrder(new Item("Pen", "", 1), inventory));
        OrderStorage storage = storage();

        storage.save(List.of(inventory));
        OrderStorage.LoadResult loaded = storage.load();

        assertEquals(2, loaded.loadedCount());
        assertTrue(loaded.warnings().isEmpty());
        Inventory restored = loaded.inventories().get(0);
        assertEquals(inventory.getName(), restored.getName());
        assertEquals(inventory.getName(), restored.getOrders().get(0).getDst());
        assertEquals("\"Rice\", 米", restored.getOrders().get(0).getItem().getName());
        assertEquals("Food, \"Grain\"", restored.getOrders().get(0).getItem().getType());
        assertEquals(0.1, restored.getOrders().get(0).getItem().getCount());
        assertEquals(inventory.getName(), restored.getOrders().get(1).getSrc());
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

        assertEquals(2, loaded.loadedCount());
        assertEquals(2, loaded.inventories().size());
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

        assertEquals(1, loaded.loadedCount());
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

        assertEquals(1, loaded.loadedCount());
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

        assertEquals(1, loaded.loadedCount());
        assertTrue(Files.exists(legacy));
    }

    @Test
    public void load_invalidFields_skipsEveryInvalidRowWithoutCreatingInventories() throws IOException {
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

        assertEquals(0, loaded.loadedCount());
        assertTrue(loaded.inventories().isEmpty());
        assertEquals(lines.size(), loaded.warnings().size());
    }

    @Test
    public void save_emptyOrders_replacesOldRowsWithHeader() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addOrder(new ImportOrder(new Item("Pen", "", 1), shop));
        OrderStorage storage = storage();
        storage.save(List.of(shop));

        storage.save(List.of());

        assertEquals(0, storage.load().loadedCount());
        assertEquals(1, Files.readAllLines(temporaryDirectory.resolve("data/orders.csv")).size());
    }

    @Test
    public void save_invalidOrder_preservesPreviouslySavedData() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addOrder(new ImportOrder(new Item("Pen", "", 1), shop));
        OrderStorage storage = storage();
        storage.save(List.of(shop));
        Path file = temporaryDirectory.resolve("data/orders.csv");
        String original = Files.readString(file);

        for (double count : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            Inventory invalid = new Inventory("Shop");
            invalid.addOrder(new ImportOrder(new Item("Rice", "", count), invalid));
            assertThrows(IOException.class, () -> storage.save(List.of(invalid)));
            assertEquals(original, Files.readString(file));
        }
        shop.addOrder(new ImportOrder(new Item("Line\nbreak", "", 1), shop));
        assertThrows(IOException.class, () -> storage.save(List.of(shop)));
        assertEquals(original, Files.readString(file));
    }

    @Test
    public void save_invalidRouteOrUnsupportedOrder_throwsIOException() {
        Inventory shop = new Inventory("Shop");
        ImportOrder invalid = new ImportOrder(new Item("Pen", "", 1), shop);
        invalid.setSrc("Warehouse");
        shop.addOrder(invalid);
        assertThrows(IOException.class, () -> storage().save(List.of(shop)));

        Inventory unsupported = new Inventory("Other");
        unsupported.addOrder(new Order(new Item("Pen", "", 1)));
        assertThrows(IOException.class, () -> storage().save(List.of(unsupported)));
    }

    @Test
    public void load_pathIsDirectory_throwsIOException() {
        assertThrows(IOException.class, () -> new OrderStorage(temporaryDirectory).load());
    }

    @Test
    public void save_parentIsFile_throwsIOExceptionAndPreservesParent() throws IOException {
        Path parent = temporaryDirectory.resolve("data");
        Files.writeString(parent, "existing data");

        assertThrows(IOException.class, () -> storage().save(List.of()));

        assertEquals("existing data", Files.readString(parent));
    }

    @Test
    public void save_targetIsNonEmptyDirectory_throwsIOExceptionAndCleansTemporaryFile() throws IOException {
        Path target = temporaryDirectory.resolve("orders.csv");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "keep");

        assertThrows(IOException.class, () -> new OrderStorage(target).save(List.of()));

        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        try (var siblings = Files.list(temporaryDirectory)) {
            assertEquals(List.of(target), siblings.toList());
        }
    }

    @Test
    public void save_rejectedOrderWithReason_roundTripsReason() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addOrder(new ImportOrder(new Item("Pen", "", 1), shop, OrderState.REJECTED, "Damaged packaging"));
        Inventory warehouse = new Inventory("Warehouse");
        warehouse.addOrder(new ExportOrder(new Item("Rice", "Food", 2.5), warehouse, OrderState.REJECTED,
                "Out of stock, \"supplier\" delay"));
        OrderStorage storage = storage();

        storage.save(List.of(shop, warehouse));
        String csv = Files.readString(temporaryDirectory.resolve("data/orders.csv"));
        assertTrue(csv.contains("Shop,REJECTED,Pen,,1.0,,Shop,Damaged packaging"));
        assertTrue(csv.contains(
                "Warehouse,REJECTED,Rice,Food,2.5,Warehouse,,\"Out of stock, \"\"supplier\"\" delay\""));

        OrderStorage.LoadResult loaded = storage.load();
        assertEquals(2, loaded.loadedCount());
        assertEquals("Damaged packaging", loaded.inventories().get(0).getOrders().get(0).getReason());
        assertEquals("Out of stock, \"supplier\" delay",
                loaded.inventories().get(1).getOrders().get(0).getReason());
    }

    @Test
    public void load_rejectedOrderWithReason_restoresReason() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason",
                "Shop,REJECTED,Pen,,1,,Shop,Damaged goods"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(1, loaded.loadedCount());
        assertTrue(loaded.warnings().isEmpty());
        Order order = loaded.inventories().get(0).getOrders().get(0);
        assertEquals(OrderState.REJECTED, order.getState());
        assertEquals("Damaged goods", order.getReason());
    }

    @Test
    public void load_legacySevenFieldRows_loadsWithNullReason() throws IOException {
        Path file = temporaryDirectory.resolve("orders.csv");
        Files.write(file, List.of(
                "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst",
                "Shop,REJECTED,Pen,,1,,Shop"), StandardCharsets.UTF_8);

        OrderStorage.LoadResult loaded = new OrderStorage(file).load();

        assertEquals(1, loaded.loadedCount());
        assertTrue(loaded.warnings().isEmpty());
        Order order = loaded.inventories().get(0).getOrders().get(0);
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

        assertEquals(0, loaded.loadedCount());
        assertEquals(1, loaded.warnings().size());
        assertTrue(loaded.warnings().get(0).contains("Only rejected orders can have a reason."));
    }

    @Test
    public void save_rejectedOrderWithLineBreakInReason_throwsIOException() {
        Inventory shop = new Inventory("Shop");
        shop.addOrder(new ImportOrder(new Item("Pen", "", 1), shop, OrderState.REJECTED, "Line\nbreak"));
        assertThrows(IOException.class, () -> storage().save(List.of(shop)));
    }
}
