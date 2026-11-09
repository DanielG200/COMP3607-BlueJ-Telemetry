package tt.edu.uwi.comp3607;

import bluej.extensions2.BClass;
import bluej.extensions2.BMethod;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.event.ClassEvent;
import bluej.extensions2.event.ClassListener;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LifecycleListener implements PackageListener, ClassListener {

    private final Path logFile;

    private final Map<String, Set<String>> methodSnapshots =
            new HashMap<>();

    private final Map<String, Set<String>> knownClassesByPackage =
            new HashMap<>();

    private final Set<String> initializedPackages =
            new HashSet<>();

    public LifecycleListener() {
        logFile = Paths.get(
                System.getProperty("user.home"),
                "Library",
                "Logs",
                "COMP3607Telemetry",
                "lifecycle.log"
        );
    }

    @Override
    public void packageOpened(PackageEvent event) {

        BPackage blueJPackage = event.getPackage();

        if (blueJPackage == null) {
            return;
        }

        StringBuilder details = new StringBuilder();

        details.append(getPackageDetails(blueJPackage));

        try {

            String packageKey = getPackageKey(blueJPackage);

            BClass[] classes = blueJPackage.getClasses();

            Set<String> knownClasses = new HashSet<>();

            details.append("Initial class count: ")
                    .append(classes.length)
                    .append("\n");

            for (BClass blueJClass : classes) {

                if (blueJClass == null) {
                    continue;
                }

                String classKey = getClassKey(blueJClass);

                knownClasses.add(classKey);

                details.append("\nInitial class: ")
                        .append(blueJClass.getName())
                        .append("\n");

                StringBuilder methodDetails = new StringBuilder();

                try {

                    trackMethods(
                            blueJClass,
                            classKey,
                            methodDetails
                    );

                    details.append(methodDetails);

                } catch (Exception exception) {

                    details.append("Initial method snapshot unavailable: ")
                            .append(exception.getMessage())
                            .append("\n");
                }
            }

            knownClassesByPackage.put(
                    packageKey,
                    knownClasses
            );

            initializedPackages.add(packageKey);

            details.append("\nPackage baseline initialized successfully.\n");

        } catch (Exception exception) {

            details.append("Could not initialize package baseline: ")
                    .append(exception.getMessage())
                    .append("\n");
        }

        logEvent("PACKAGE_OPENED", details.toString());
    }

    @Override
    public void packageClosing(PackageEvent event) {

        BPackage blueJPackage = event.getPackage();

        if (blueJPackage == null) {
            return;
        }

        StringBuilder details = new StringBuilder();

        details.append(getPackageDetails(blueJPackage));

        try {

            String packageKey = getPackageKey(blueJPackage);

            Set<String> knownClasses =
                    knownClassesByPackage.remove(packageKey);

            initializedPackages.remove(packageKey);

            if (knownClasses != null) {

                for (String classKey : knownClasses) {
                    methodSnapshots.remove(classKey);
                }
            }

            details.append("Package tracking state cleared.\n");

        } catch (Exception exception) {

            details.append("Could not clear package tracking state: ")
                    .append(exception.getMessage())
                    .append("\n");
        }

        logEvent("PACKAGE_CLOSING", details.toString());
    }

    @Override
    public void classStateChanged(ClassEvent event) {

        BClass blueJClass = event.getBClass();

        if (blueJClass == null) {
            return;
        }

        String className = blueJClass.getName();

        String classKey = getClassKey(blueJClass);

        StringBuilder details = new StringBuilder();

        details.append("Class: ")
                .append(className)
                .append("\n");

        details.append("Event type: ")
                .append(event.getEventType())
                .append("\n");

        details.append("Compiled: ")
                .append(event.isClassCompiled())
                .append("\n");

        details.append("Has error: ")
                .append(event.hasError())
                .append("\n");

        File sourceFile = getSourceFile(blueJClass);

        if (sourceFile != null) {

            details.append("Source file: ")
                    .append(sourceFile.getAbsolutePath())
                    .append("\n");

        } else {

            details.append("Source file: Not available yet\n");
        }

        try {

            BPackage blueJPackage = blueJClass.getPackage();

            String packageKey = getPackageKey(blueJPackage);

            if (initializedPackages.contains(packageKey)) {

                Set<String> knownClasses =
                        knownClassesByPackage.get(packageKey);

                if (knownClasses != null
                        && knownClasses.add(classKey)) {

                    details.append("New class detected: ")
                            .append(className)
                            .append("\n");

                    logEvent(
                            "CLASS_CREATED",
                            "Class: " + className
                                    + "\nClass key: " + classKey + "\n"
                    );
                }
            }

        } catch (Exception exception) {

            details.append("Class creation tracking unavailable: ")
                    .append(exception.getMessage())
                    .append("\n");
        }

        if (event.isClassCompiled()) {

            try {

                trackMethods(
                        blueJClass,
                        classKey,
                        details
                );

            } catch (Exception exception) {

                details.append("Method tracking unavailable: ")
                        .append(exception.getMessage())
                        .append("\n");
            }
        }

        logEvent("CLASS_STATE_CHANGED", details.toString());
    }

    @Override
    public void classNameChanged(ClassEvent event) {

        BClass blueJClass = event.getBClass();

        StringBuilder details = new StringBuilder();

        details.append("Old name: ")
                .append(event.getOldName())
                .append("\n");

        if (blueJClass != null) {

            String newName = blueJClass.getName();

            details.append("New name: ")
                    .append(newName)
                    .append("\n");

            try {

                String packageKey =
                        getPackageKey(blueJClass.getPackage());

                Set<String> knownClasses =
                        knownClassesByPackage.get(packageKey);

                if (knownClasses != null) {
                    knownClasses.add(getClassKey(blueJClass));
                }

            } catch (Exception exception) {

                details.append("Could not update renamed class tracking: ")
                        .append(exception.getMessage())
                        .append("\n");
            }
        }

        logEvent("CLASS_RENAMED", details.toString());
    }

    @Override
    public void classRemoved(ClassEvent event) {

        BClass blueJClass = event.getBClass();

        StringBuilder details = new StringBuilder();

        if (blueJClass != null) {

            String className = blueJClass.getName();

            String classKey = getClassKey(blueJClass);

            details.append("Removed class: ")
                    .append(className)
                    .append("\n");

            details.append("Class key: ")
                    .append(classKey)
                    .append("\n");

            methodSnapshots.remove(classKey);

            try {

                String packageKey =
                        getPackageKey(blueJClass.getPackage());

                Set<String> knownClasses =
                        knownClassesByPackage.get(packageKey);

                if (knownClasses != null) {
                    knownClasses.remove(classKey);
                }

            } catch (Exception exception) {

                details.append("Could not update removed class tracking: ")
                        .append(exception.getMessage())
                        .append("\n");
            }
        }

        logEvent("CLASS_REMOVED", details.toString());
    }

    private void trackMethods(
            BClass blueJClass,
            String classKey,
            StringBuilder details
    ) throws Exception {

        Set<String> currentMethods = new HashSet<>();

        BMethod[] methods = blueJClass.getDeclaredMethods();

        details.append("Methods returned by BlueJ: ")
                .append(methods.length)
                .append("\n");

        for (BMethod method : methods) {

            StringBuilder signature = new StringBuilder();

            signature.append(method.getName())
                    .append("(");

            Class<?>[] parameters =
                    method.getParameterTypes();

            for (int i = 0; i < parameters.length; i++) {

                if (i > 0) {
                    signature.append(", ");
                }

                signature.append(
                        parameters[i].getSimpleName()
                );
            }

            signature.append(") : ");

            signature.append(
                    method.getReturnType().getSimpleName()
            );

            currentMethods.add(signature.toString());
        }

        Set<String> previousMethods =
                methodSnapshots.get(classKey);

        details.append("Previous snapshot size: ")
                .append(previousMethods == null ? 0 : previousMethods.size())
                .append("\n");

        details.append("Current snapshot size: ")
                .append(currentMethods.size())
                .append("\n");

        if (previousMethods == null) {

            details.append("Initial method snapshot:\n");

            for (String method : currentMethods) {

                details.append("Method: ")
                        .append(method)
                        .append("\n");
            }

        } else {

            for (String method : currentMethods) {

                if (!previousMethods.contains(method)) {

                    details.append("New method detected: ")
                            .append(method)
                            .append("\n");
                }
            }

            for (String method : previousMethods) {

                if (!currentMethods.contains(method)) {

                    details.append("Method removed: ")
                            .append(method)
                            .append("\n");
                }
            }
        }

        methodSnapshots.put(classKey, currentMethods);
    }

    private File getSourceFile(BClass blueJClass) {

        try {
            return blueJClass.getJavaFile();
        } catch (Exception exception) {
            return null;
        }
    }

    private String getClassKey(BClass blueJClass) {

        File sourceFile = getSourceFile(blueJClass);

        if (sourceFile != null) {
            return sourceFile.getAbsolutePath();
        }

        try {
            return getPackageKey(blueJClass.getPackage())
                    + File.separator
                    + blueJClass.getName();
        } catch (Exception exception) {
            return blueJClass.getName();
        }
    }

    private String getPackageKey(BPackage blueJPackage)
            throws Exception {

        if (blueJPackage == null) {
            throw new Exception("BlueJ package is unavailable.");
        }

        File packageDirectory = blueJPackage.getDir();

        if (packageDirectory != null) {
            return packageDirectory.getAbsolutePath();
        }

        return blueJPackage.getName();
    }

    private String getPackageDetails(BPackage blueJPackage) {

        StringBuilder details = new StringBuilder();

        try {

            details.append("Package: ")
                    .append(blueJPackage.getName())
                    .append("\n");

            File packageDirectory = blueJPackage.getDir();

            if (packageDirectory != null) {

                details.append("Package directory: ")
                        .append(packageDirectory.getAbsolutePath())
                        .append("\n");
            }

            BProject project = blueJPackage.getProject();

            details.append("Project: ")
                    .append(project.getName())
                    .append("\n");

            details.append("Project directory: ")
                    .append(project.getDir().getAbsolutePath())
                    .append("\n");

        } catch (Exception exception) {

            details.append("Some package details unavailable: ")
                    .append(exception.getMessage())
                    .append("\n");
        }

        return details.toString();
    }

    private void logEvent(
            String eventName,
            String details
    ) {

        StringBuilder record = new StringBuilder();

        record.append("Event: ")
                .append(eventName)
                .append("\n");

        record.append("Timestamp: ")
                .append(Instant.now())
                .append("\n");

        record.append(details);

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
                    "[COMP3607 Telemetry] Could not write lifecycle log: "
                            + exception.getMessage()
            );
        }
    }
}
