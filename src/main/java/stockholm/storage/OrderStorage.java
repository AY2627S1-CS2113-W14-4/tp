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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;
import stockholm.order.ExportOrder;
import stockholm.order.ImportOrder;
import stockholm.order.Order;
import stockholm.order.OrderState;

/** Saves only orders, across all inventories, as UTF-8 CSV with one order per physical line. */
public class OrderStorage {
    /** Relative to the working directory; change this constant to relocate the default save file. */
    public static final String DATA_FILE_PATH = "./data/orders.csv";
    /** Where orders were saved before the file was renamed; it is moved to {@link #DATA_FILE_PATH} on load. */
    public static final String LEGACY_DATA_FILE_PATH = "./data/OrdersData.csv";
    private static final Logger LOGGER = Logger.getLogger(OrderStorage.class.getName());
    private static final String HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst,Reason";
    /** Header written before the file was renamed; still accepted on load. */
    private static final String LEGACY_HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,src,dst";
    /** Header written before rejection reasons were stored; still accepted on load. */
    private static final String PRE_REASON_HEADER = "Inventory,OrderState,ItemName,ItemType,ItemCount,Src,Dst";
    private static final int FIELD_COUNT = 8;
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

    /** Loaded inventories contain orders only; warnings retain the original erroneous lines. */
    public record LoadResult(List<Inventory> inventories, List<String> warnings, int loadedCount) {
        public LoadResult {
            inventories = List.copyOf(inventories);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Loads valid rows in file order, collecting a warning for each malformed row.
     * A missing file represents a first launch; other read failures are raised to the caller.
     * If only the legacy file exists, it is first moved to the current path so its orders carry over.
     *
     * @return restored orders grouped by inventory, warnings, and the successful order count
     * @throws IOException if the file cannot be read, or the legacy file cannot be moved
     */
    public LoadResult load() throws IOException {
        moveLegacyFile();
        Map<String, Inventory> inventories = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int loadedCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && (HEADER.equals(line) || LEGACY_HEADER.equals(line)
                        || PRE_REASON_HEADER.equals(line))) {
                    continue;
                }
                try {
                    StoredOrder stored = parseOrder(line);
                    Inventory inventory = inventories.computeIfAbsent(stored.inventoryName(), Inventory::new);
                    inventory.addOrder(stored.order());
                    loadedCount++;
                } catch (IOException e) {
                    warnings.add("Skipped order on line " + lineNumber + " in " + filePath + ": \"" + line
                            + "\" (" + e.getMessage() + ")");
                }
            }
        } catch (NoSuchFileException e) {
            return new LoadResult(List.of(), List.of(), 0);
        }
        return new LoadResult(new ArrayList<>(inventories.values()), warnings, loadedCount);
    }

    /** Moves the legacy save file to the current path if only the legacy file exists. */
    private void moveLegacyFile() throws IOException {
        if (legacyFilePath != null && Files.notExists(filePath) && Files.exists(legacyFilePath)) {
            Files.move(legacyFilePath, filePath);
        }
    }

    /**
     * Replaces the shared CSV with all current orders, preserving order within each inventory.
     * Writes to a temporary sibling before replacement so a failed write leaves the previous data intact.
     *
     * @param inventories all inventories, including ones whose orders have not changed
     * @throws IOException if data is invalid or the directory or file cannot be written
     */
    public void save(List<Inventory> inventories) throws IOException {
        assert inventories != null : "Saving orders requires an inventory list.";
        LOGGER.log(Level.FINE, "Saving orders to {0}", filePath);
        try {
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            for (Inventory inventory : inventories) {
                assert inventory != null : "An inventory being saved must not be null.";
                for (Order order : inventory.getOrders()) {
                    lines.add(serializeOrder(inventory, order));
                }
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

    private String serializeOrder(Inventory inventory, Order order) throws IOException {
        assert order != null : "An order being saved must not be null.";
        if (!(order instanceof ImportOrder) && !(order instanceof ExportOrder)) {
            throw new IOException("Only import and export orders can be saved.");
        }
        Item item = order.getItem();
        assert item != null : "A saved order must have an item.";
        assert order.getState() != null : "A saved order must have a state.";
        String reason = order.getReason() == null ? "" : order.getReason();
        String line = String.join(",", encodeField(inventory.getName()), encodeField(order.getState().name()),
                encodeField(item.getName()), encodeField(item.getType()), Double.toString(item.getCount()),
                encodeField(order.getSrc() == null ? "" : order.getSrc()),
                encodeField(order.getDst() == null ? "" : order.getDst()),
                encodeField(reason));
        // Apply the same validation on save and load so every saved row can be restored.
        parseOrder(line);
        assert line.indexOf('\n') < 0 && line.indexOf('\r') < 0
                : "Each saved order must occupy one physical CSV line.";
        return line;
    }

    private record StoredOrder(String inventoryName, Order order) {
    }

    private StoredOrder parseOrder(String line) throws IOException {
        List<String> fields = parseCsv(line);
        if (fields.size() != FIELD_COUNT && fields.size() != LEGACY_FIELD_COUNT) {
            throw new IOException("Expected " + FIELD_COUNT + " CSV fields.");
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
        if (fields.size() == FIELD_COUNT) {
            String rawReason = fields.get(7);
            if (!rawReason.isEmpty()) {
                if (state != OrderState.REJECTED) {
                    throw new IOException("Only rejected orders can have a reason.");
                }
                reason = rawReason;
            }
        }
        Item item = new Item(itemName, fields.get(3), count);
        Inventory inventory = new Inventory(inventoryName);
        if (src.isEmpty() && dst.equals(inventoryName)) {
            return new StoredOrder(inventoryName, new ImportOrder(item, inventory, state, reason));
        }
        if (dst.isEmpty() && src.equals(inventoryName)) {
            return new StoredOrder(inventoryName, new ExportOrder(item, inventory, state, reason));
        }
        throw new IOException("Exactly one of src or dst must match the owning inventory.");
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
