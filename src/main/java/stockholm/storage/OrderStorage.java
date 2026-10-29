package stockholm.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderHistory;
import stockholm.order.OrderState;
import stockholm.order.TransferOrder;

/**
 * Saves the {@link OrderHistory}, across all inventories, as UTF-8 CSV with one order per physical line,
 * in the order the orders were created.
 */
public class OrderStorage {
    /** Relative to the working directory; change this constant to relocate the default save file. */
    public static final String DATA_FILE_PATH = "./data/orders.csv";
    /** Where orders were saved before the file was renamed; it is moved to {@link #DATA_FILE_PATH} on load. */
    public static final String LEGACY_DATA_FILE_PATH = "./data/OrdersData.csv";
    private static final Logger LOGGER = Logger.getLogger(OrderStorage.class.getName());
    private static final String HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason,ItemMin";
    /** Header written before the file was renamed; still accepted on load. */
    private static final String LEGACY_HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,src,dst";
    /** Header written before rejection reasons were stored; still accepted on load. */
    private static final String PRE_REASON_HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst";
    /** Header written before item minimums were stored; still accepted on load. */
    private static final String PRE_MIN_HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason";
    private static final int FIELD_COUNT = 9;
    private static final int PRE_MIN_FIELD_COUNT = 8;
    private static final int LEGACY_FIELD_COUNT = 7;

    private final Path filePath;
    /** An older save file to move to {@code filePath} if only it exists, or {@code null} for none. */
    private final Path legacyFilePath;

    /** Uses the project's shared order save file, taking over the legacy file if only that exists. */
    public OrderStorage() {
        this(Path.of(DATA_FILE_PATH), Path.of(LEGACY_DATA_FILE_PATH));
    }

    /** Uses a supplied path, e.g. an isolated file in a test. */
    public OrderStorage(Path filePath) {
        this(filePath, null);
    }

    /**
     * Uses a supplied path, moving {@code legacyFilePath} to it on load if only the legacy file exists.
     *
     * @param filePath the save file
     * @param legacyFilePath an older save file to take over, or {@code null} for none
     */
    public OrderStorage(Path filePath, Path legacyFilePath) {
        this.filePath = Objects.requireNonNull(filePath);
        this.legacyFilePath = legacyFilePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    /** Restored orders in file order; warnings retain the original erroneous lines. */
    public record LoadResult(List<Order> orders, List<String> warnings) {
        public LoadResult {
            orders = List.copyOf(orders);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Loads valid rows in file order, collecting a warning for each malformed row.
     * A missing file represents a first launch; other read failures are raised to the caller.
     * If only the legacy file exists, it is first moved to the current path so its orders carry over.
     *
     * @return restored orders in file order, and warnings
     * @throws IOException if the file cannot be read, or the legacy file cannot be moved
     */
    public LoadResult load() throws IOException {
        moveLegacyFile();
        List<Order> orders = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && (HEADER.equals(line) || LEGACY_HEADER.equals(line)
                        || PRE_REASON_HEADER.equals(line) || PRE_MIN_HEADER.equals(line))) {
                    continue;
                }
                try {
                    orders.add(parseOrder(line));
                } catch (IOException e) {
                    warnings.add("Skipped order on line " + lineNumber + " in " + filePath + ": \"" + line
                            + "\" (" + e.getMessage() + ")");
                }
            }
        } catch (NoSuchFileException e) {
            return new LoadResult(List.of(), List.of());
        }
        return new LoadResult(orders, warnings);
    }

    /** Moves the legacy save file to the current path if only the legacy file exists. */
    private void moveLegacyFile() throws IOException {
        if (legacyFilePath != null && Files.notExists(filePath) && Files.exists(legacyFilePath)) {
            Files.move(legacyFilePath, filePath);
        }
    }

    /**
     * Replaces the shared CSV with every order in the history, in creation order.
     * Writes to a temporary sibling before replacement so a failed write leaves the previous data intact.
     *
     * @param history all orders, including ones that have not changed
     * @throws IOException if data is invalid or the directory or file cannot be written
     */
    public void save(OrderHistory history) throws IOException {
        assert history != null : "Saving orders requires an order history.";
        LOGGER.log(Level.FINE, "Saving orders to {0}", filePath);
        try {
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            for (Order order : history.getOrders()) {
                lines.add(serializeOrder(order));
            }
            assert HEADER.equals(lines.getFirst()) : "Saved orders must start with the CSV header.";
            Path target = filePath.toAbsolutePath();
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "orders-", ".tmp");
            assert !temporary.equals(target) : "Orders must be written separately before replacing the save file.";
            assert temporary.getParent().equals(target.getParent())
                    : "The temporary order file must be a sibling of the save file.";
            try {
                Files.write(temporary, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    LOGGER.log(Level.FINE, "Atomic order save unavailable; using replacement move for {0}", target);
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            LOGGER.log(Level.FINE, "Saved {0} order(s) to {1}", new Object[]{lines.size() - 1, target});
        } catch (IOException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Failed to save orders to " + filePath, e);
            throw e;
        }
    }

    private String serializeOrder(Order order) throws IOException {
        assert order != null : "An order being saved must not be null.";
        if (!(order instanceof ImportOrder) && !(order instanceof ExportOrder) && !(order instanceof TransferOrder)) {
            throw new IOException("Only import, export and transfer orders can be saved.");
        }
        Item item = order.getItem();
        assert item != null : "A saved order must have an item.";
        assert order.getState() != null : "A saved order must have a state.";
        String reason = order.getReason() == null ? "" : order.getReason();
        String line = String.join(",", encodeField(order.getInventoryName()), encodeField(order.getState().name()),
                encodeField(item.getName()), encodeField(item.getType()), Double.toString(item.getCount()),
                encodeField(order.getSrc() == null ? "" : order.getSrc()),
                encodeField(order.getDst() == null ? "" : order.getDst()),
                encodeField(reason),
                // An item without a minimum leaves the field empty, as the inventory file leaves it out.
                item.getMinCount() > 0 ? Double.toString(item.getMinCount()) : "");
        // Apply the same validation on save and load so every saved row can be restored.
        parseOrder(line);
        assert line.indexOf('\n') < 0 && line.indexOf('\r') < 0
                : "Each saved order must occupy one physical CSV line.";
        return line;
    }

    private Order parseOrder(String line) throws IOException {
        List<String> fields = parseCsv(line);
        if (fields.size() != FIELD_COUNT && fields.size() != PRE_MIN_FIELD_COUNT
                && fields.size() != LEGACY_FIELD_COUNT) {
            throw new IOException("Expected " + FIELD_COUNT + " (or legacy " + PRE_MIN_FIELD_COUNT + " or "
                    + LEGACY_FIELD_COUNT + ") CSV fields.");
        }
        String inventoryName = fields.get(0);
        String itemName = fields.get(2);
        if (inventoryName.isBlank() || itemName.isBlank()) {
            throw new IOException("Inventory and item names must not be blank.");
        }
        OrderState state;
        double count;
        try {
            state = OrderState.valueOf(fields.get(1));
            count = Double.parseDouble(fields.get(4));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid order state or item count.", e);
        }
        if (!Double.isFinite(count) || count <= 0) {
            throw new IOException("Item count must be positive and finite.");
        }
        String src = fields.get(5);
        String dst = fields.get(6);
        String reason = null;
        if (fields.size() >= PRE_MIN_FIELD_COUNT) {
            String rawReason = fields.get(7);
            if (!rawReason.isEmpty()) {
                if (state != OrderState.REJECTED) {
                    throw new IOException("Only rejected orders can have a reason.");
                }
                reason = rawReason;
            }
        }
        Item item = new Item(itemName, fields.get(3), count);
        if (fields.size() == FIELD_COUNT && !fields.get(8).isEmpty()) {
            item.setMinCount(parseMin(fields.get(8)));
        }
        Inventory inventory = new Inventory(inventoryName);
        if (src.isEmpty() && dst.equals(inventoryName)) {
            return new ImportOrder(item, inventory, state, reason);
        }
        if (dst.isEmpty() && src.equals(inventoryName)) {
            return new ExportOrder(item, inventory, state, reason);
        }
        if (src.equals(inventoryName) && !dst.isBlank() && !src.equals(dst)) {
            return new TransferOrder(item, inventory, new Inventory(dst), state, reason);
        }
        throw new IOException("Invalid order endpoints: imports need a destination, exports need a source, "
                + "and transfers need distinct inventories with the source as owner.");
    }

    /** Parses a saved item minimum, which must be a finite, non-negative number. */
    private static double parseMin(String field) throws IOException {
        double min;
        try {
            min = Double.parseDouble(field);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid item minimum.", e);
        }
        if (!Double.isFinite(min) || min < 0) {
            throw new IOException("Item minimum must be finite and non-negative.");
        }
        return min;
    }

    private String encodeField(String field) throws IOException {
        if (field == null || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
            throw new IOException("CSV fields must be non-null and contain no line breaks.");
        }
        if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /** Parses a single CSV row, including quoted commas, escaped quotes, and trailing empty fields. */
    private List<String> parseCsv(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        int position = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            if (position < line.length() && line.charAt(position) == '"') {
                position++;
                boolean closed = false;
                while (position < line.length()) {
                    char character = line.charAt(position++);
                    if (character != '"') {
                        field.append(character);
                    } else if (position < line.length() && line.charAt(position) == '"') {
                        field.append('"');
                        position++;
                    } else {
                        closed = true;
                        break;
                    }
                }
                if (!closed || (position < line.length() && line.charAt(position) != ',')) {
                    throw new IOException("Invalid quoted CSV field.");
                }
            } else {
                while (position < line.length() && line.charAt(position) != ',') {
                    char character = line.charAt(position++);
                    if (character == '"') {
                        throw new IOException("Quote in an unquoted CSV field.");
                    }
                    field.append(character);
                }
            }
            fields.add(field.toString());
            if (position == line.length()) {
                return fields;
            }
            position++;
        }
    }
}
