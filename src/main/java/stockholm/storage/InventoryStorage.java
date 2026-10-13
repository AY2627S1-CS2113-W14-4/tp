package stockholm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import stockholm.inventory.Inventory;
import stockholm.inventory.Item;

/**
 * Saves inventories and their items, but not their orders, as pretty-printed JSON using Gson.
 *
 * <p>The file format is defined by the private records below rather than by {@link Inventory}
 * and {@link Item} themselves, so the model classes can change without breaking saved files.
 */
public class InventoryStorage {
    /** Relative to the working directory; change this constant to relocate the default save file. */
    public static final String DATA_FILE_PATH = "./data/inventories.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path filePath;

    /** Uses the project's shared inventory save file. */
    public InventoryStorage() {
        this(Path.of(DATA_FILE_PATH));
    }

    /** Uses a supplied path, e.g. an isolated file in a test. */
    public InventoryStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath);
    }

    public Path getFilePath() {
        return filePath;
    }

    /** Loaded inventories contain items only; each warning describes one skipped inventory or item. */
    public record LoadResult(List<Inventory> inventories, List<String> warnings, int loadedCount) {
        public LoadResult {
            inventories = List.copyOf(inventories);
            warnings = List.copyOf(warnings);
        }
    }

    /** Thrown when the file exists and can be read, but is not valid inventory JSON as a whole. */
    public static class CorruptFileException extends IOException {
        public CorruptFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Loads every valid inventory and item in file order, collecting a warning for each invalid one.
     * A missing file represents a first launch.
     *
     * @return restored inventories, warnings, and the number of inventories loaded
     * @throws CorruptFileException if the file is not valid JSON or has no inventory list
     * @throws IOException if the file cannot be read
     */
    public LoadResult load() throws IOException {
        String json;
        try {
            json = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new LoadResult(List.of(), List.of(), 0);
        }
        SavedData data;
        try {
            data = GSON.fromJson(json, SavedData.class);
        } catch (JsonParseException e) {
            throw new CorruptFileException("Invalid JSON: " + e.getMessage(), e);
        }
        if (data == null || data.inventories() == null) {
            throw new CorruptFileException("No inventory list found.", null);
        }

        List<Inventory> inventories = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < data.inventories().size(); i++) {
            SavedInventory saved = data.inventories().get(i);
            String problem = findProblem(saved, names);
            if (problem != null) {
                warnings.add("Skipped inventory " + (i + 1) + " in " + filePath + ": " + problem + ".");
                continue;
            }
            names.add(saved.name());
            Inventory inventory = new Inventory(saved.name());
            for (int j = 0; j < saved.items().size(); j++) {
                SavedItem item = saved.items().get(j);
                String itemProblem = findProblem(item);
                if (itemProblem != null) {
                    warnings.add("Skipped item " + (j + 1) + " of " + saved.name() + ": " + itemProblem + ".");
                    continue;
                }
                String type = (item.type() == null) ? "" : item.type();
                inventory.addItem(new Item(item.name(), type, item.count()));
            }
            inventories.add(inventory);
        }
        return new LoadResult(inventories, warnings, inventories.size());
    }

    /**
     * Replaces the file with all inventories and their items, preserving their order.
     * Writes to a temporary sibling before replacement so a failed write leaves the previous data intact.
     *
     * @param inventories all inventories
     * @throws IOException if the directory or file cannot be written
     */
    public void save(List<Inventory> inventories) throws IOException {
        List<SavedInventory> savedInventories = new ArrayList<>();
        for (Inventory inventory : inventories) {
            List<SavedItem> savedItems = new ArrayList<>();
            for (Item item : inventory.getItems()) {
                savedItems.add(new SavedItem(item.getName(), item.getType(), item.getCount()));
            }
            savedInventories.add(new SavedInventory(inventory.getName(), savedItems));
        }

        Path target = filePath.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "inventories-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(new SavedData(savedInventories)), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Copies the file to a {@code .bak} sibling, replacing an older backup, so data that could not be
     * loaded is not lost when the file is next saved.
     *
     * @return the backup's path
     * @throws IOException if the copy fails
     */
    public Path backUp() throws IOException {
        Path backup = filePath.resolveSibling(filePath.getFileName() + ".bak");
        Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    /** Returns why a saved inventory cannot be loaded, or {@code null} if it is valid. */
    private static String findProblem(SavedInventory saved, Set<String> namesSoFar) {
        if (saved == null || saved.name() == null || saved.name().isBlank()) {
            return "no name";
        }
        if (namesSoFar.contains(saved.name())) {
            return "name " + saved.name() + " appears more than once";
        }
        if (saved.items() == null) {
            return saved.name() + " has no item list";
        }
        return null;
    }

    /** Returns why a saved item cannot be loaded, or {@code null} if it is valid. */
    private static String findProblem(SavedItem item) {
        if (item == null || item.name() == null || item.name().isBlank()) {
            return "no name";
        }
        if (item.count() == null || !Double.isFinite(item.count()) || item.count() <= 0) {
            return item.name() + " has a missing or non-positive count";
        }
        return null;
    }

    /** The whole file: every inventory. */
    private record SavedData(List<SavedInventory> inventories) {
    }

    /** One inventory and its items; orders are saved separately by {@link OrderStorage}. */
    private record SavedInventory(String name, List<SavedItem> items) {
    }

    /** One item. {@code count} is boxed so a missing count can be told apart from zero. */
    private record SavedItem(String name, String type, Double count) {
    }
}
