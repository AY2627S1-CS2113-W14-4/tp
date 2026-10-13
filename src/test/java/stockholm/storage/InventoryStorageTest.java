package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ImportOrder;

/** Tests the inventory JSON format using an isolated file in a temporary folder. */
class InventoryStorageTest {
    @TempDir
    private Path temporaryDirectory;

    private Path file;
    private InventoryStorage storage;

    @BeforeEach
    public void setUp() {
        file = temporaryDirectory.resolve("data").resolve("inventories.json");
        storage = new InventoryStorage(file);
    }

    @Test
    public void load_missingFile_returnsEmptyResult() throws IOException {
        InventoryStorage.LoadResult result = storage.load();
        assertTrue(result.inventories().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertEquals(0, result.loadedCount());
    }

    @Test
    public void save_missingFolder_createsFolderAndFileWithoutTemporaryFiles() throws IOException {
        storage.save(List.of(new Inventory("Shop")));

        assertTrue(Files.exists(file));
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    public void saveThenLoad_inventoriesAndItems_roundTrip() throws IOException {
        Inventory shop = new Inventory("Main Shop");
        shop.addItem(new Item("A4 Paper Case", "Stationery", 2));
        shop.addItem(new Item("Pen", "", 1));
        shop.addItem(new Item("Rice", "Food", 2.5));

        storage.save(List.of(shop, new Inventory("Warehouse")));
        InventoryStorage.LoadResult result = storage.load();

        assertEquals(2, result.loadedCount());
        assertTrue(result.warnings().isEmpty());
        assertEquals("Main Shop", result.inventories().get(0).getName());
        assertEquals("Warehouse", result.inventories().get(1).getName());
        assertTrue(result.inventories().get(1).getItems().isEmpty());
        List<Item> items = result.inventories().get(0).getItems();
        assertEquals(3, items.size());
        assertEquals("A4 Paper Case (Stationery) x2", items.get(0).toString());
        assertEquals("Pen x1", items.get(1).toString());
        assertEquals("Rice (Food) x2.5", items.get(2).toString());
    }

    @Test
    public void saveThenLoad_inventoryWithOrder_ordersNotSaved() throws IOException {
        Inventory shop = new Inventory("Shop");
        shop.addOrder(new ImportOrder(new Item("Ink", "", 5), shop));

        storage.save(List.of(shop));

        assertTrue(storage.load().inventories().get(0).getOrders().isEmpty());
    }

    @Test
    public void save_existingFile_replacesIt() throws IOException {
        storage.save(List.of(new Inventory("Old")));
        storage.save(List.of(new Inventory("New")));

        List<Inventory> loaded = storage.load().inventories();
        assertEquals(1, loaded.size());
        assertEquals("New", loaded.get(0).getName());
    }

    @Test
    public void load_missingType_usesEmptyType() throws IOException {
        writeFile("{\"inventories\": [{\"name\": \"Shop\", \"items\": [{\"name\": \"Pen\", \"count\": 3}]}]}");
        assertEquals("Pen x3", storage.load().inventories().get(0).getItems().get(0).toString());
    }

    @Test
    public void load_corruptFile_throwsCorruptFileException() throws IOException {
        for (String content : new String[]{"not json", "{broken", "{}", "null", "[]"}) {
            writeFile(content);
            assertThrows(InventoryStorage.CorruptFileException.class, () -> storage.load(),
                    "should reject: " + content);
        }
    }

    @Test
    public void load_unreadableFile_throwsPlainIoException() throws IOException {
        Files.createDirectories(file);
        IOException e = assertThrows(IOException.class, () -> storage.load());
        assertTrue(!(e instanceof InventoryStorage.CorruptFileException));
    }

    @Test
    public void load_invalidEntries_skipsThemWithWarningsAndKeepsTheRest() throws IOException {
        writeFile("{\"inventories\": ["
                + "{\"name\": \"Shop\", \"items\": ["
                + "{\"name\": \"Pen\", \"count\": 2},"
                + "{\"name\": \"Zero\", \"count\": 0},"
                + "{\"name\": \"Negative\", \"count\": -1},"
                + "{\"name\": \"Missing\"},"
                + "{\"count\": 1}"
                + "]},"
                + "{\"items\": []},"
                + "{\"name\": \"Shop\", \"items\": []},"
                + "{\"name\": \"NoItems\"},"
                + "{\"name\": \"Warehouse\", \"items\": []}"
                + "]}");

        InventoryStorage.LoadResult result = storage.load();

        assertEquals(2, result.loadedCount());
        assertEquals("Shop", result.inventories().get(0).getName());
        assertEquals("Warehouse", result.inventories().get(1).getName());
        assertEquals(1, result.inventories().get(0).getItems().size());
        assertEquals("Pen x2", result.inventories().get(0).getItems().get(0).toString());
        assertEquals(7, result.warnings().size());
    }

    private void writeFile(String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
