package stockholm.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.command.Command;
import stockholm.exceptions.StockHolmException;
import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.parser.Parser;
import stockholm.state.AppState;
import stockholm.storage.InventoryStorage;
import stockholm.storage.OrderStorage;
import stockholm.storage.Storage;

/** Tests CSV import through the command path, including all-or-nothing validation. */
class ItemCsvImportTest {
    @TempDir
    Path tempDir;

    /** Creates an entered Shop with an existing Pen and its minimum stock level. */
    private AppState shopWithPen() {
        AppState state = new AppState();
        Inventory shop = new Inventory("Shop");
        Item pen = new Item("Pen", "Stationery", 2);
        pen.setMinCount(4);
        shop.addItem(pen);
        state.getInventories().add(shop);
        state.enterInventory(shop);
        return state;
    }

    /** Writes a CSV file with a space in its name and returns its path. */
    private Path writeCsv(String content) throws Exception {
        Path file = tempDir.resolve("bulk items.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Builds the same quoted command a user types for a path with spaces. */
    private static Command importCommand(Path file) throws StockHolmException {
        return Parser.parseCommand("item import \"" + file + "\"");
    }

    @Test
    public void itemImport_quotedFieldsAndExistingItem_mergesAndPersists() throws Exception {
        AppState state = shopWithPen();
        Path csv = writeCsv("name,type,count,minCount\n"
                + "\"Blue, Pen\",Office,3,5\n"
                + "Pen,Stationery,1,\n"
                + "\"New \"\"Quote\"\"\",,2,0\n");
        Command command = importCommand(csv);

        Result result = Processor.process(command, state);

        Inventory shop = state.getCurrentInventory();
        assertEquals("Imported 3 item row(s) into Shop.", result.message());
        assertEquals(3, shop.getItems().size());
        assertEquals("Pen", shop.getItems().get(0).getName());
        assertEquals(3.0, shop.getItems().get(0).getCount());
        assertEquals(4.0, shop.getItems().get(0).getMinCount());
        assertEquals("Blue, Pen", shop.getItems().get(1).getName());
        assertEquals(5.0, shop.getItems().get(1).getMinCount());
        assertEquals("New \"Quote\"", shop.getItems().get(2).getName());

        Path saved = tempDir.resolve("inventories.json");
        new Storage(new OrderStorage(tempDir.resolve("orders.csv")), new InventoryStorage(saved))
                .saveAfterCommand(command, state);
        assertEquals(3, new InventoryStorage(saved).load().inventories().get(0).getItems().size());
    }

    @Test
    public void itemImport_conflictingLaterRow_leavesInventoryUnchanged() throws Exception {
        AppState state = shopWithPen();
        Inventory shop = state.getCurrentInventory();
        Item original = shop.getItems().get(0);
        Path csv = writeCsv("name,type,count,minCount\nRice,Food,2,0\nPen,Office,1,0\n");

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(csv), state));

        assertTrue(e.getMessage().startsWith("CSV row 3: Item already exists with a different type:"));
        assertEquals(1, shop.getItems().size());
        assertSame(original, shop.getItems().get(0));
        assertEquals(2.0, original.getCount());
    }

    @Test
    public void itemImport_invalidCountAndMalformedCsv_reportsRowWithoutChanges() throws Exception {
        AppState state = shopWithPen();
        Inventory shop = state.getCurrentInventory();
        Path invalidCount = writeCsv("name,type,count,minCount\nRice,Food,2,0\nBean,Food,-1,0\n");
        StockHolmException countError = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(invalidCount), state));
        assertTrue(countError.getMessage().startsWith("CSV row 3: count must be"));
        assertEquals(1, shop.getItems().size());

        Path malformed = writeCsv("name,type,count,minCount\nRice,Food,2,0\n\"Bean,Food,1,0\n");
        StockHolmException quoteError = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(malformed), state));
        assertEquals("CSV row 3: unclosed quoted field.", quoteError.getMessage());
        assertEquals(1, shop.getItems().size());
    }

    @Test
    public void itemImport_wrongHeaderAndInvalidMinimum_rejectsWholeFile() throws Exception {
        AppState state = shopWithPen();
        Path wrongHeader = writeCsv("item,type,count,minCount\nRice,Food,2,0\n");
        StockHolmException headerError = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(wrongHeader), state));
        assertEquals("CSV header must be name,type,count,minCount.", headerError.getMessage());

        Path invalidMinimum = writeCsv("name,type,count,minCount\nRice,Food,2,0\nBean,Food,1,-1\n");
        StockHolmException minimumError = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(invalidMinimum), state));
        assertTrue(minimumError.getMessage().startsWith("CSV row 3: minCount must be"));
        assertEquals(1, state.getCurrentInventory().getItems().size());
    }

    @Test
    public void itemImport_conflictingRowsInFile_rejectsWholeFile() throws Exception {
        AppState state = shopWithPen();
        Path csv = writeCsv("name,type,count,minCount\nRice,Food,2,\nRice,Stationery,1,\n");

        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(csv), state));

        assertTrue(e.getMessage().startsWith("CSV row 3: Item already exists with a different type:"));
        assertEquals(1, state.getCurrentInventory().getItems().size());
    }

    @Test
    public void itemImport_missingFile_reportsPathWithoutChanges() throws StockHolmException {
        AppState state = shopWithPen();
        Path missing = tempDir.resolve("missing.csv");
        StockHolmException e = assertThrows(StockHolmException.class,
                () -> Processor.process(importCommand(missing), state));
        assertTrue(e.getMessage().startsWith("Cannot read CSV file " + missing));
        assertEquals(1, state.getCurrentInventory().getItems().size());
    }
}
