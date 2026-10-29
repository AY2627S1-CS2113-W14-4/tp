package stockholm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import stockholm.exceptions.StockHolmException;

/** Reads item details from a UTF-8 CSV file before any inventory is changed. */
public final class ItemCsvReader {
    private static final List<String> HEADER = List.of("name", "type", "count", "minCount");
    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

    private ItemCsvReader() {
    }

    /** One validated item row; a missing minimum leaves an existing item's minimum unchanged. */
    public record Row(int number, String name, String type, double count, Double minimum) {
    }

    /**
     * Reads and validates every CSV row, including quoted commas and escaped quotes.
     * A malformed file returns no rows and cannot partially change an inventory.
     *
     * @param path the user-selected UTF-8 CSV file
     * @return all validated rows after the header
     * @throws IOException if the file cannot be read
     * @throws StockHolmException if the header or any row is invalid
     */
    public static List<Row> read(Path path) throws IOException, StockHolmException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<List<String>> records = parseRecords(text);
        if (records.isEmpty() || !HEADER.equals(records.getFirst())) {
            throw new StockHolmException("CSV header must be name,type,count,minCount.");
        }
        if (records.size() == 1) {
            throw new StockHolmException("CSV file has no item rows.");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            int number = i + 1;
            List<String> fields = records.get(i);
            if (fields.size() != HEADER.size()) {
                throw new StockHolmException("CSV row " + number + ": expected 4 columns, found "
                        + fields.size() + ".");
            }
            String name = fields.get(0).trim();
            String type = fields.get(1).trim();
            if (name.isEmpty() || hasLineBreak(name)) {
                throw new StockHolmException("CSV row " + number + ": name must be nonblank and on one line.");
            }
            if (hasLineBreak(type)) {
                throw new StockHolmException("CSV row " + number + ": type must be on one line.");
            }
            double count = parseDecimal(fields.get(2).trim(), number, "count", true);
            String minimumText = fields.get(3).trim();
            Double minimum = minimumText.isEmpty()
                    ? null : parseDecimal(minimumText, number, "minCount", false);
            rows.add(new Row(number, name, type, count, minimum));
        }
        return List.copyOf(rows);
    }

    /** Returns a finite decimal with the sign required by the field, or reports its row. */
    private static double parseDecimal(String value, int row, String field, boolean mustBePositive)
            throws StockHolmException {
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            number = Double.NaN;
        }
        if (!DECIMAL.matcher(value).matches() || !Double.isFinite(number)
                || (mustBePositive && number <= 0)) {
            String rule = mustBePositive ? "a finite positive decimal" : "a finite non-negative decimal";
            throw new StockHolmException("CSV row " + row + ": " + field + " must be " + rule + ": " + value + ".");
        }
        return number;
    }

    /** Returns whether a field would split the app's line-oriented item display. */
    private static boolean hasLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    /** Parses CSV records while respecting quoted commas, line breaks, and doubled quotes. */
    private static List<List<String>> parseRecords(String text) throws StockHolmException {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean atStart = true;
        boolean recordStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }
            if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                atStart = true;
                afterQuote = false;
                recordStarted = true;
                continue;
            }
            if (character == '\n' || character == '\r') {
                if (character == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                records.add(List.copyOf(fields));
                fields.clear();
                field.setLength(0);
                atStart = true;
                afterQuote = false;
                recordStarted = false;
                continue;
            }
            if (afterQuote || (character == '"' && !atStart)) {
                throw new StockHolmException("CSV row " + (records.size() + 1) + ": invalid quote.");
            }
            if (character == '"') {
                quoted = true;
            } else {
                field.append(character);
            }
            atStart = false;
            recordStarted = true;
        }
        if (quoted) {
            throw new StockHolmException("CSV row " + (records.size() + 1) + ": unclosed quoted field.");
        }
        if (recordStarted || !fields.isEmpty() || afterQuote) {
            fields.add(field.toString());
            records.add(List.copyOf(fields));
        }
        return records;
    }
}
