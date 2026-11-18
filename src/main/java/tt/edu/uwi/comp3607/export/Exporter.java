package tt.edu.uwi.comp3607.export;

import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Saves a whole {@link TelemetrySession} to a file in one particular format.
 *
 * Design pattern: Strategy. JSON and PDF are two different ways of saving the
 * same session, so each one is its own class that implements this interface.
 * TelemetryExtension just keeps a list of exporters and calls
 * {@link #export(TelemetrySession, Path)} on each one, without needing to know
 * which formats there are.
 *
 * This also follows the Open/Closed principle: to add a new format (like CSV),
 * you write a new class that implements this interface and add it to the list.
 * None of the existing code has to change.
 *
 * Known implementations: {@link JsonExporter} and {@link PdfExporter}. Both are
 * made in TelemetryExtension at startup, and export is called on each one when
 * BlueJ closes. To write your own, make sure export creates any missing folders
 * and replaces an existing file with the same name.
 *
 * @author Kirese Tasha Maharaj
 * @see JsonExporter
 * @see PdfExporter
 */

public interface Exporter {

/**
 * Saves the session to a file in this exporter's format. Any folders in the
 * path that don't exist yet are created, and an existing file with the same
 * name is replaced.
 *
 * @param session the session to save; can't be {@code null}
 * @param target  where to save the file, including its name
 * @throws IOException if the file or its folders can't be written
 */
    void export(TelemetrySession session, Path target) throws IOException;

/**
 * Returns the file extension for this format without the dot, e.g.
 * {@code "json"} or {@code "pdf"}. TelemetryExtension uses it to name the file.
 */
    String fileExtension();
}