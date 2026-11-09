package tt.edu.uwi.comp3607;

import bluej.extensions2.BClass;
import bluej.extensions2.BMethod;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.event.ClassEvent;
import bluej.extensions2.event.ClassListener;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;
import tt.edu.uwi.comp3607.tracking.LifecycleRecorder;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * BlueJ adapter for project and class lifecycle events. It reads the BlueJ
 * objects and hands plain values to LifecycleRecorder, which records the
 * events through the shared TelemetrySession.
 */
public class LifecycleListener implements PackageListener, ClassListener {

    private final LifecycleRecorder recorder;

    public LifecycleListener(TelemetrySession session, EventFactory eventFactory) {
        this.recorder = new LifecycleRecorder(session, eventFactory);
    }

    @Override
    public void packageOpened(PackageEvent event) {
        BPackage blueJPackage = event.getPackage();
        if (blueJPackage == null) {
            return;
        }
        try {
            BProject project = blueJPackage.getProject();
            recorder.projectOpened(project.getDir().getAbsolutePath(), project.getName());

            String packageKey = getPackageKey(blueJPackage);
            recorder.beginPackage(packageKey);

            for (BClass blueJClass : blueJPackage.getClasses()) {
                if (blueJClass != null) {
                    recorder.baselineClass(
                            packageKey, getClassKey(blueJClass), getMethodSignatures(blueJClass));
                }
            }
        } catch (Exception exception) {
            report("package opened", exception);
        }
    }

    @Override
    public void packageClosing(PackageEvent event) {
        BPackage blueJPackage = event.getPackage();
        if (blueJPackage == null) {
            return;
        }
        try {
            BProject project = blueJPackage.getProject();
            recorder.packageClosed(getPackageKey(blueJPackage));
            recorder.projectClosed(project.getDir().getAbsolutePath(), project.getName());
        } catch (Exception exception) {
            report("package closing", exception);
        }
    }

    @Override
    public void classStateChanged(ClassEvent event) {
        BClass blueJClass = event.getBClass();
        if (blueJClass == null) {
            return;
        }
        try {
            String packageKey = getPackageKey(blueJClass.getPackage());
            boolean compiled = event.isClassCompiled();
            Set<String> methods = compiled ? getMethodSignatures(blueJClass) : null;
            recorder.classChanged(
                    packageKey, getClassKey(blueJClass), blueJClass.getName(), compiled, methods);
        } catch (Exception exception) {
            report("class state change", exception);
        }
    }

    @Override
    public void classNameChanged(ClassEvent event) {
        BClass blueJClass = event.getBClass();
        if (blueJClass == null) {
            return;
        }
        try {
            String oldName = event.getOldName();
            String packageKey = getPackageKey(blueJClass.getPackage());
            recorder.classRenamed(
                    packageKey,
                    getOldClassKey(blueJClass, oldName, packageKey),
                    getClassKey(blueJClass),
                    oldName,
                    blueJClass.getName());
        } catch (Exception exception) {
            report("class rename", exception);
        }
    }

    @Override
    public void classRemoved(ClassEvent event) {
        BClass blueJClass = event.getBClass();
        if (blueJClass == null) {
            return;
        }
        String className = blueJClass.getName();
        String packageKey;
        String classKey;
        try {
            packageKey = getPackageKey(blueJClass.getPackage());
            classKey = getClassKey(blueJClass);
        } catch (Exception exception) {
            packageKey = "";
            classKey = className;
        }
        recorder.classRemoved(packageKey, classKey, className);
    }

    // ---------- BlueJ helpers ----------

    /** Signatures like "add(int, int) : int", or null if BlueJ cannot list them yet. */
    private Set<String> getMethodSignatures(BClass blueJClass) {
        try {
            Set<String> signatures = new TreeSet<>();
            for (BMethod method : blueJClass.getDeclaredMethods()) {
                StringBuilder signature = new StringBuilder();
                signature.append(method.getName()).append("(");

                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) {
                        signature.append(", ");
                    }
                    signature.append(parameters[i].getSimpleName());
                }
                signature.append(") : ").append(method.getReturnType().getSimpleName());
                signatures.add(signature.toString());
            }
            return signatures;
        } catch (Exception exception) {
            return null;
        }
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
            return getPackageKey(blueJClass.getPackage()) + File.separator + blueJClass.getName();
        } catch (Exception exception) {
            return blueJClass.getName();
        }
    }

    /** The key the class had before it was renamed. */
    private String getOldClassKey(BClass blueJClass, String oldName, String packageKey) {
        File sourceFile = getSourceFile(blueJClass);
        if (sourceFile != null) {
            return new File(sourceFile.getParentFile(), oldName + ".java").getAbsolutePath();
        }
        return packageKey + File.separator + oldName;
    }

    private String getPackageKey(BPackage blueJPackage) throws Exception {
        if (blueJPackage == null) {
            throw new Exception("BlueJ package is unavailable.");
        }
        File packageDirectory = blueJPackage.getDir();
        if (packageDirectory != null) {
            return packageDirectory.getAbsolutePath();
        }
        return blueJPackage.getName();
    }

    private void report(String what, Exception exception) {
        System.err.println("[Telemetry] Lifecycle tracking failed (" + what + "): " + exception.getMessage());
    }
}
