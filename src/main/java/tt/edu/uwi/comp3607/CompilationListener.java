package tt.edu.uwi.comp3607;

import bluej.extensions2.event.CompileEvent;
import bluej.extensions2.event.CompileListener;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;

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
    private final TelemetrySession session;
    private final EventFactory eventFactory;

    public CompilationListener(TelemetrySession session, EventFactory eventFactory) {
        this.session = session;
        this.eventFactory = eventFactory;
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
        session.record(eventFactory.compileStarted(firstFile(event)));
    }

    @Override
    public void compileError(CompileEvent event) {
        logEvent("COMPILATION_ERROR", event);
        session.record(eventFactory.compileError(
                firstFile(event), event.getErrorLineNumber(), event.getErrorMessage()));
    }

    @Override
    public void compileWarning(CompileEvent event) {
        logEvent("COMPILATION_WARNING", event);
        session.record(eventFactory.compileWarning(
                firstFile(event), event.getErrorLineNumber(), event.getErrorMessage()));
    }

    @Override
    public void compileSucceeded(CompileEvent event) {
        logEvent("COMPILATION_SUCCEEDED", event);
        session.record(eventFactory.compileSucceeded(firstFile(event)));
    }

    @Override
    public void compileFailed(CompileEvent event) {
        logEvent("COMPILATION_FAILED", event);
        session.record(eventFactory.compileFailed(firstFile(event)));
    }

    /** Name of the first file in the compile, or null if BlueJ gave none. */
    private static String firstFile(CompileEvent event) {
        File[] files = event.getFiles();
        return (files == null || files.length == 0) ? null : files[0].getName();
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