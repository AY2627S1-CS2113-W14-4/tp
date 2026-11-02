package stockholm;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs StockHolm as a separate process, as a user would from a terminal. Each run starts with fresh static state
 * and reads and writes its save files in {@code data} under the given working directory, so tests never touch the
 * project's own data folder.
 */
final class AppRunner {
    private static final long TIMEOUT_SECONDS = 30;

    private final Path workingDirectory;

    AppRunner(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /** What one run printed, with line endings normalized to {@code \n}, and its exit code. */
    record Output(String text, int exitCode) {
    }

    /**
     * Starts the app, types each line followed by Enter, then closes its input as Ctrl+D would.
     *
     * @param inputLines the commands to type, in order
     * @return everything the app printed, and its exit code
     * @throws IllegalStateException if not run through {@code ./gradlew e2eTest}, or the app does not exit in time
     */
    Output run(String... inputLines) throws IOException, InterruptedException {
        String classpath = System.getProperty("stockholm.classpath");
        if (classpath == null) {
            throw new IllegalStateException("Run the end-to-end tests with ./gradlew e2eTest");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-ea", "-Dstdout.encoding=UTF-8", "-Dstdin.encoding=UTF-8",
                "-cp", classpath, "stockholm.StockHolm")
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        // Read output while typing, so a full output pipe can never block the app.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try (Writer input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            for (String line : inputLines) {
                input.write(line + "\n");
            }
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException();
            }
            String text = output.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).replace("\r\n", "\n");
            return new Output(text, process.exitValue());
        } catch (TimeoutException e) {
            throw new IllegalStateException("StockHolm did not exit within " + TIMEOUT_SECONDS + " seconds", e);
        } catch (ExecutionException e) {
            throw new IOException("Could not read the app's output", e.getCause());
        } finally {
            process.destroyForcibly();
        }
    }
}
