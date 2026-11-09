package tt.edu.uwi.comp3607;

import bluej.extensions2.BlueJ;
import bluej.extensions2.Extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class TelemetryExtension extends Extension {

    @Override
    public boolean isCompatible() {
        return true;
    }

    @Override
    public void startup(BlueJ bluej) {

        // Register compilation telemetry
        bluej.addCompileListener(new CompilationListener());

        // Register project and class lifecycle telemetry
        LifecycleListener lifecycleListener = new LifecycleListener();

        bluej.addPackageListener(lifecycleListener);
        bluej.addClassListener(lifecycleListener);

        System.out.println("COMP3607 Telemetry Extension Loaded");
        System.out.println("Compilation listener registered!");
        System.out.println("Lifecycle listener registered!");

        // Startup diagnostic
        try {
            Path logFile = Paths.get(
                    System.getProperty("user.home"),
                    "Library",
                    "Logs",
                    "COMP3607Telemetry",
                    "startup.log"
            );

            Files.createDirectories(logFile.getParent());

            String message = "Extension startup executed: "
                    + Instant.now()
                    + System.lineSeparator();

            Files.write(
                    logFile,
                    message.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException exception) {
            System.err.println(
                    "[COMP3607 Telemetry] Startup diagnostic failed: "
                            + exception.getMessage()
            );
        }
    }

    @Override
    public String getName() {
        return "COMP3607 Telemetry";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Collects BlueJ project, editor, compilation and execution telemetry.";
    }
}
