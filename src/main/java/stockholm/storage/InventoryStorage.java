package stockholm.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** The name given to a saved inventory whose name is missing, or blank after cleaning. */
    static final String EMPTY_NAME = "_";
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

    /**
     * Loaded inventories contain items only; each warning describes one skipped or renamed inventory, or one
     * skipped item. {@code renamedInventories} maps each saved name that was changed on load to its new name, so
     * orders naming that inventory can follow it; {@code namesChanged} is whether any inventory was renamed, including
     * ones left out of that map, so the file can be saved with the new names.
     */
    public record LoadResult(List<Inventory> inventories, List<String> warnings, int loadedCount,
            Map<String, String> renamedInventories, boolean namesChanged) {
        public LoadResult {
            inventories = List.copyOf(inventories);
            warnings = List.copyOf(warnings);
            renamedInventories = Map.copyOf(renamedInventories);
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
            return new LoadResult(List.of(), List.of(), 0, Map.of(), false);
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
        Map<String, String> renamedInventories = new HashMap<>();
        boolean namesChanged = false;
        List<String> names = assignNames(data.inventories());
        for (int i = 0; i < data.inventories().size(); i++) {
            SavedInventory saved = data.inventories().get(i);
            String problem = findProblem(saved);
            if (problem != null) {
                warnings.add("Skipped inventory " + (i + 1) + " in " + filePath + ": " + problem + ".");
                continue;
            }
            String name = names.get(i);
            if (!name.equals(saved.name())) {
                namesChanged = true;
                warnings.add("Renamed inventory " + (i + 1) + " in " + filePath + " from "
                        + (saved.name() == null ? "no name" : "\"" + saved.name() + "\"") + " to " + name + ", "
                        + renameReason(saved.name(), name) + ".");
                // Orders naming a saved name that another inventory kept unchanged stay with that inventory.
                if (saved.name() != null && !names.contains(saved.name())) {
                    renamedInventories.putIfAbsent(saved.name(), name);
                }
            }
            Inventory inventory = new Inventory(name);
            for (int j = 0; j < saved.items().size(); j++) {
                SavedItem item = saved.items().get(j);
                String itemProblem = findProblem(item);
                if (itemProblem != null) {
                    warnings.add("Skipped item " + (j + 1) + " of " + name + " in " + filePath + ": "
                            + itemProblem + ".");
                    continue;
                }
                String type = (item.type() == null) ? "" : item.type();
                Item loadedItem = new Item(item.name(), type, item.count());
                if (isValidMin(item.min())) {
                    loadedItem.setMinCount(item.min() == null ? 0 : item.min());
                } else {
                    warnings.add("Ignored the minimum of " + item.name() + " in " + name + " in " + filePath
                            + ": it must be a number of 0 or more.");
                }
                inventory.addItem(loadedItem);
            }
            inventories.add(inventory);
        }
        return new LoadResult(inventories, warnings, inventories.size(), renamedInventories, namesChanged);
    }

    /**
     * Chooses a unique, typeable name for every loadable saved inventory, by position; others get {@code null}.
     * Names that are already clean are kept, first come first served. The rest are cleaned, and a name still taken
     * gets the lowest free {@code " (n)"} suffix from 2, e.g. {@code Shop (2)} or {@code _ (2)}.
     */
    private static List<String> assignNames(List<SavedInventory> saved) {
        List<String> names = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (SavedInventory inventory : saved) {
            String name = null;
            if (findProblem(inventory) == null && inventory.name() != null
                    && inventory.name().equals(cleanName(inventory.name())) && taken.add(inventory.name())) {
                name = inventory.name();
            }
            names.add(name);
        }
        for (int i = 0; i < saved.size(); i++) {
            if (names.get(i) != null || findProblem(saved.get(i)) != null) {
                continue;
            }
            String base = cleanName(saved.get(i).name());
            String name = base;
            for (int n = 2; taken.contains(name); n++) {
                name = base + " (" + n + ")";
            }
            taken.add(name);
            names.set(i, name);
        }
        return names;
    }

    /** Explains why a saved inventory name was changed to {@code newName} on load. */
    private static String renameReason(String savedName, String newName) {
        String cleaned = cleanName(savedName);
        if (!cleaned.equals(newName)) {
            return "as another inventory is already named " + cleaned;
        }
        if (cleaned.equals(EMPTY_NAME)) {
            return "as inventory names cannot be blank";
        }
        return "as inventory names cannot contain double quotes or leading or trailing spaces";
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
                // A missing minimum is saved as null, which Gson leaves out, so files without minimums stay unchanged.
                Double min = (item.getMinCount() > 0) ? item.getMinCount() : null;
                savedItems.add(new SavedItem(item.getName(), item.getType(), item.getCount(), min));
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
     * Returns a saved inventory name as one that commands can type: double quotes removed, then leading and trailing
     * whitespace trimmed. Names saved before inventory commands stripped quotes may otherwise be unreachable.
     *
     * @param savedName the name as read from a save file, or {@code null} if it is missing
     * @return the cleaned name, or {@link #EMPTY_NAME} if nothing but quotes and whitespace remains
     */
    static String cleanName(String savedName) {
        String name = (savedName == null) ? "" : savedName.replace("\"", "").trim();
        // isBlank, as trim() keeps Unicode whitespace such as U+3000, and an inventory name must not be blank.
        return name.isBlank() ? EMPTY_NAME : name;
    }

    /** Returns why a saved inventory cannot be loaded, or {@code null} if it is valid. Its name is never a problem. */
    private static String findProblem(SavedInventory saved) {
        if (saved == null) {
            return "it is empty";
        }
        if (saved.items() == null) {
            return (saved.name() == null ? "it" : saved.name()) + " has no item list";
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

    /**
     * Returns whether a saved minimum can be used. A missing minimum (from files saved before minimums existed,
     * or for items without one) is valid and means none.
     */
    private static boolean isValidMin(Double min) {
        return min == null || (Double.isFinite(min) && min >= 0);
    }

    /** The whole file: every inventory. */
    private record SavedData(List<SavedInventory> inventories) {
    }

    /** One inventory and its items; orders are saved separately by {@link OrderStorage}. */
    private record SavedInventory(String name, List<SavedItem> items) {
    }

    /**
     * One item. {@code count} is boxed so a missing count can be told apart from zero;
     * {@code min} is boxed so items without a minimum can leave it out.
     */
    private record SavedItem(String name, String type, Double count, Double min) {
    }
}
