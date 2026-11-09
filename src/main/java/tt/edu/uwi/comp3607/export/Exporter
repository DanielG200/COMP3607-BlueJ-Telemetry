package tt.edu.uwi.comp3607.export;

import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.file.Path;

/**
 * DESIGN PATTERN: Strategy.
 *
 * Each output format (JSON, PDF etc) is a separate class with the same
 * export() method. The extension can loop over a list of exporters
 * without knowing which formats exist, so adding a new format means
 * adding a class, not editing old ones (Open/Closed principle).
 */
public interface Exporter {
    void export(TelemetrySession session, Path target) throws IOException;
    String fileExtension();
}