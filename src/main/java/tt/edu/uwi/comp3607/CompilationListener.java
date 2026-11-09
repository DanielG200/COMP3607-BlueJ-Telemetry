package tt.edu.uwi.comp3607;

import bluej.extensions2.event.CompileEvent;
import bluej.extensions2.event.CompileListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

public class CompilationListener implements CompileListener {

    private final Path logFile;

    public CompilationListener() {
        logFile = Paths.get(
                System.getProperty("user.home"),
                "Library",
                "Logs",
                "COMP3607Telemetry",
                "compilation.log"
        );
    }

    @Override
    public void compileStarted(CompileEvent event) {
        logEvent("COMPILATION_STARTED", event);
    }

    @Override
    public void compileError(CompileEvent event) {
        logEvent("COMPILATION_ERROR", event);
    }

    @Override
    public void compileWarning(CompileEvent event) {
        logEvent("COMPILATION_WARNING", event);
    }

    @Override
    public void compileSucceeded(CompileEvent event) {
        logEvent("COMPILATION_SUCCEEDED", event);
    }

    @Override
    public void compileFailed(CompileEvent event) {
        logEvent("COMPILATION_FAILED", event);
    }

    private void logEvent(String eventName, CompileEvent event) {

        StringBuilder record = new StringBuilder();

        record.append("Event: ").append(eventName).append("\n");
        record.append("Timestamp: ").append(Instant.now()).append("\n");
        record.append("User initiated: ")
                .append(event.isUserGeneratedCompilation()).append("\n");

        File[] files = event.getFiles();

        if (files != null) {
            for (File file : files) {
                record.append("File: ")
                        .append(file.getAbsolutePath())
                        .append("\n");
            }
        }

        if (event.getErrorMessage() != null) {
            record.append("Message: ")
                    .append(event.getErrorMessage())
                    .append("\n");
        }

        if (event.getErrorLineNumber() > 0) {
            record.append("Error line: ")
                    .append(event.getErrorLineNumber())
                    .append("\n");
        }

        int[] position = event.getErrorPosition();

        if (position != null) {
            record.append("Error position: ")
                    .append(Arrays.toString(position))
                    .append("\n");
        }

        record.append("----------------------------------------\n");

        try {
            Files.createDirectories(logFile.getParent());

            Files.write(
                    logFile,
                    record.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException exception) {
            System.err.println(
                    "[COMP3607 Telemetry] Could not write log: "
                            + exception.getMessage()
            );
        }
    }
}
