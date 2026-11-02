package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderHistory;
import stockholm.order.OrderState;

/** Tests timestamp persistence and compatibility with orders saved before timestamps existed. */
class OrderTimestampStorageTest {
    private static final String HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason,"
            + "ItemMin,CreatedAt,LastStatusChangedAt";

    @TempDir
    private Path temporaryDirectory;

    /** Preserves both instants through a save and load. */
    @Test
    public void saveAndLoad_preservesTimestamps() throws IOException {
        Instant created = Instant.parse("2026-10-01T10:00:00Z");
        Instant changed = Instant.parse("2026-10-02T11:30:00Z");
        Order order = new ImportOrder(new Item("Pen", "", 1), new Inventory("Shop"),
                OrderState.WAITING_FOR_DELIVERY, null, created, changed);
        OrderHistory history = new OrderHistory();
        history.add(order);
        OrderStorage storage = new OrderStorage(temporaryDirectory.resolve("orders.csv"));

        storage.save(history);
        OrderStorage.LoadResult loaded = storage.load();

        assertTrue(loaded.warnings().isEmpty());
        assertEquals(created, loaded.orders().getFirst().getCreatedAt());
        assertEquals(changed, loaded.orders().getFirst().getLastStatusChangedAt());
    }

    /** Treats older rows as having unknown dates, including after saving them in the new format. */
    @Test
    public void legacyOrder_doesNotGainFalseTimestampOnLoadOrSave() throws IOException {
        Path path = temporaryDirectory.resolve("orders.csv");
        Files.write(path, List.of("Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason,ItemMin",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,,"));
        OrderStorage storage = new OrderStorage(path);
        Order order = storage.load().orders().getFirst();

        assertNull(order.getCreatedAt());
        assertNull(order.getLastStatusChangedAt());
        OrderHistory history = new OrderHistory();
        history.add(order);
        storage.save(history);
        assertEquals(HEADER, Files.readAllLines(path).getFirst());
        assertNull(storage.load().orders().getFirst().getCreatedAt());
        assertNull(storage.load().orders().getFirst().getLastStatusChangedAt());
    }

    /** Skips rows with malformed or chronologically impossible timestamps and reports their line numbers. */
    @Test
    public void invalidTimestamps_warnAndSkipOnlyInvalidRows() throws IOException {
        Path path = temporaryDirectory.resolve("orders.csv");
        Files.write(path, List.of(HEADER,
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,,,bad,2026-10-01T10:00:00Z",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,,,2026-10-02T10:00:00Z,2026-10-01T10:00:00Z",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,,,2026-10-01T10:00:00Z,",
                "Shop,WAITING_APPROVAL,Pen,,1,,Shop,,,2026-10-01T10:00:00Z,2026-10-01T10:00:00Z"));

        OrderStorage.LoadResult loaded = new OrderStorage(path).load();

        assertEquals(1, loaded.orders().size());
        assertEquals(3, loaded.warnings().size());
        assertTrue(loaded.warnings().get(0).contains("line 2"));
        assertTrue(loaded.warnings().get(1).contains("line 3"));
        assertTrue(loaded.warnings().get(2).contains("line 4"));
    }
}
