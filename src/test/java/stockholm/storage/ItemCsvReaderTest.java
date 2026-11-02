package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import stockholm.exceptions.StockHolmException;

/** Tests reading and validating item CSV files, independently of the {@code item import} command. */
class ItemCsvReaderTest {
    private static final String HEADER = "name,type,count,minCount\n";

    @TempDir
    private Path temporaryDirectory;

    @Test
    public void read_byteOrderMarkCrlfAndNoFinalLineBreak_readsEveryRow() throws IOException, StockHolmException {
        Path file = write("﻿name,type,count,minCount\r\n Pen , Office ,2.5,1\r\nInk,,3,");

        List<ItemCsvReader.Row> rows = ItemCsvReader.read(file);

        assertEquals(List.of(new ItemCsvReader.Row(2, "Pen", "Office", 2.5, 1.0),
                new ItemCsvReader.Row(3, "Ink", "", 3, null)), rows);
    }

    @Test
    public void read_quotedFields_keepCommasAndDoubledQuotes() throws IOException, StockHolmException {
        Path file = write(HEADER + "\"Blue, \"\"Gel\"\" Pen\",\"Office, Desk\",1,0\n");

        ItemCsvReader.Row row = ItemCsvReader.read(file).getFirst();

        assertEquals("Blue, \"Gel\" Pen", row.name());
        assertEquals("Office, Desk", row.type());
        assertEquals(0.0, row.minimum());
    }

    @Test
    public void read_emptyFileOrHeaderOnly_throwsException() throws IOException {
        assertRejected("", "CSV header must be name,type,count,minCount.");
        assertRejected(HEADER, "CSV file has no item rows.");
    }

    @Test
    public void read_wrongColumnCount_reportsRowAndCount() throws IOException {
        assertRejected(HEADER + "Pen,Office,1,0\nInk,,3\n", "CSV row 3: expected 4 columns, found 3.");
    }

    @Test
    public void read_blankOrMultiLineName_throwsException() throws IOException {
        String message = "CSV row 2: name must be nonblank and on one line.";
        assertRejected(HEADER + "  ,Office,1,0\n", message);
        assertRejected(HEADER + "\"Blue\nPen\",Office,1,0\n", message);
    }

    @Test
    public void read_multiLineType_throwsException() throws IOException {
        assertRejected(HEADER + "Pen,\"Office\r\nDesk\",1,0\n", "CSV row 2: type must be on one line.");
    }

    @Test
    public void read_invalidCount_throwsException() throws IOException {
        for (String count : new String[]{"abc", "1e3", "-1", "0", ""}) {
            assertRejected(HEADER + "Pen,," + count + ",\n",
                    "CSV row 2: count must be a finite positive decimal: " + count + ".");
        }
    }

    @Test
    public void read_invalidMinimum_throwsException() throws IOException {
        for (String minimum : new String[]{"-1", "x", "1e2"}) {
            assertRejected(HEADER + "Pen,,1," + minimum + "\n",
                    "CSV row 2: minCount must be a finite non-negative decimal: " + minimum + ".");
        }
    }

    @Test
    public void read_misplacedOrUnclosedQuote_throwsException() throws IOException {
        assertRejected(HEADER + "Pe\"n,,1,\n", "CSV row 2: invalid quote.");
        assertRejected(HEADER + "\"Pen\"x,,1,\n", "CSV row 2: invalid quote.");
        assertRejected(HEADER + "\"Pen,,1,\n", "CSV row 2: unclosed quoted field.");
    }

    @Test
    public void read_missingFile_throwsIoException() {
        assertThrows(IOException.class, () -> ItemCsvReader.read(temporaryDirectory.resolve("missing.csv")));
    }

    /** Writes {@code content} as a UTF-8 CSV file and returns its path. */
    private Path write(String content) throws IOException {
        Path file = temporaryDirectory.resolve("items.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Asserts that reading {@code content} is rejected with exactly {@code message}. */
    private void assertRejected(String content, String message) throws IOException {
        Path file = write(content);
        StockHolmException e = assertThrows(StockHolmException.class, () -> ItemCsvReader.read(file));
        assertEquals(message, e.getMessage());
    }
}
