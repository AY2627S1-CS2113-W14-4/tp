package stockholm.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Makes timestamped copies of a save file, e.g. {@code inventories.json.2026-10-13T14-03-22.bak},
 * so data that could not be loaded survives the next save. Older backups are never replaced.
 */
final class BackupFile {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private BackupFile() {
    }

    /**
     * Backs up {@code file} using the current local time.
     *
     * @see #create(Path, Clock)
     */
    static Path create(Path file) throws IOException {
        return create(file, Clock.systemDefaultZone());
    }

    /**
     * Copies {@code file} to a new timestamped backup next to it. If an existing backup already has exactly
     * the same contents, no copy is made and that backup is returned, so restarting on the same bad file
     * does not pile up identical copies. A second backup within the same second gets a {@code -1}, {@code -2}, ...
     * suffix instead of replacing the first.
     *
     * @param file the save file to back up; must exist
     * @param clock the source of the timestamp
     * @return the backup's path
     * @throws IOException if the folder cannot be read or the copy fails
     */
    static Path create(Path file, Clock clock) throws IOException {
        Path existing = findIdenticalBackup(file);
        if (existing != null) {
            return existing;
        }
        String base = file.getFileName() + "." + LocalDateTime.now(clock).format(TIMESTAMP);
        Path backup = file.resolveSibling(base + ".bak");
        for (int suffix = 1; Files.exists(backup); suffix++) {
            backup = file.resolveSibling(base + "-" + suffix + ".bak");
        }
        return Files.copy(file, backup);
    }

    /** Returns an existing backup of {@code file} with identical contents, or {@code null} if there is none. */
    private static Path findIdenticalBackup(Path file) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        String glob = file.getFileName() + ".*.bak";
        try (DirectoryStream<Path> backups = Files.newDirectoryStream(folder, glob)) {
            for (Path backup : backups) {
                if (Files.isRegularFile(backup) && Files.mismatch(file, backup) == -1) {
                    return file.resolveSibling(backup.getFileName());
                }
            }
        }
        return null;
    }
}
