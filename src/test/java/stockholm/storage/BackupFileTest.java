package stockholm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests timestamped backups with a fixed clock, so the backup names are predictable. */
class BackupFileTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-13T14:03:22Z"), ZoneOffset.UTC);

    @TempDir
    private Path temporaryDirectory;

    private Path file;

    @BeforeEach
    public void setUp() {
        file = temporaryDirectory.resolve("inventories.json");
    }

    @Test
    public void create_existingFile_copiesToTimestampedBackupAndKeepsOriginal() throws IOException {
        Files.writeString(file, "data");

        Path backup = BackupFile.create(file, FIXED_CLOCK);

        assertEquals(temporaryDirectory.resolve("inventories.json.2026-10-13T14-03-22.bak"), backup);
        assertEquals("data", Files.readString(backup));
        assertEquals("data", Files.readString(file));
    }

    @Test
    public void create_differentContentsSameSecond_addsSuffixInsteadOfReplacing() throws IOException {
        Files.writeString(file, "first");
        Path first = BackupFile.create(file, FIXED_CLOCK);
        Files.writeString(file, "second");
        Path second = BackupFile.create(file, FIXED_CLOCK);
        Files.writeString(file, "third");
        Path third = BackupFile.create(file, FIXED_CLOCK);

        assertEquals(temporaryDirectory.resolve("inventories.json.2026-10-13T14-03-22-1.bak"), second);
        assertEquals(temporaryDirectory.resolve("inventories.json.2026-10-13T14-03-22-2.bak"), third);
        assertEquals("first", Files.readString(first));
        assertEquals("second", Files.readString(second));
        assertEquals("third", Files.readString(third));
    }

    @Test
    public void create_identicalBackupExists_returnsItWithoutCopying() throws IOException {
        Files.writeString(file, "same");
        Path first = BackupFile.create(file, FIXED_CLOCK);
        Path later = BackupFile.create(file, Clock.offset(FIXED_CLOCK, Duration.ofHours(1)));

        assertEquals(first, later);
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    public void create_onlyMatchesBackupsOfTheSameFile() throws IOException {
        Path other = temporaryDirectory.resolve("OrdersData.csv");
        Files.writeString(other, "same");
        Path otherBackup = BackupFile.create(other, FIXED_CLOCK);
        Files.writeString(file, "same");

        Path backup = BackupFile.create(file, FIXED_CLOCK);

        assertNotEquals(otherBackup, backup);
        assertEquals(temporaryDirectory.resolve("inventories.json.2026-10-13T14-03-22.bak"), backup);
    }

    @Test
    public void create_missingFile_throwsIoException() {
        assertThrows(IOException.class, () -> BackupFile.create(file, FIXED_CLOCK));
    }
}
