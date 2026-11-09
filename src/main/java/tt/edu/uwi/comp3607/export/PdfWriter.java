package tt.edu.uwi.comp3607.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A very small PDF generator: A4 pages, Helvetica text, rectangles and lines.
 *
 * Written by hand so the plugin needs no extra library (a BlueJ extension
 * is one jar, and third party jars are awkward to bundle). It only does what
 * the telemetry report needs. Its one job is turning drawing commands into
 * valid PDF bytes; PdfExporter decides what to draw (Single Responsibility).
 *
 * Coordinates are in points, with (0, 0) at the bottom left of the page.
 */
public class PdfWriter {

    public static final float PAGE_WIDTH = 595f;
    public static final float PAGE_HEIGHT = 842f;

    /** Helvetica glyph widths (per 1000 units) for the characters 32 to 126. */
    private static final int[] HELVETICA_WIDTHS = {
            278, 278, 355, 556, 556, 889, 667, 222, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
    };
    private static final float BOLD_FACTOR = 1.08f;

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder current;

    public PdfWriter() {
        newPage();
    }

    // ---------- Pages ----------

    /** Starts a new page and makes it the one being drawn on. */
    public void newPage() {
        current = new StringBuilder();
        pages.add(current);
    }

    public int pageCount() {
        return pages.size();
    }

    /** Switches drawing to an existing page (0 is the first). Used for footers. */
    public void selectPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IllegalArgumentException("No page " + index);
        }
        current = pages.get(index);
    }

    // ---------- Drawing ----------

    public void text(float x, float y, float size, boolean bold, float[] rgb, String text) {
        current.append(color(rgb, false))
                .append("BT /").append(bold ? "F2" : "F1").append(' ').append(num(size)).append(" Tf ")
                .append(num(x)).append(' ').append(num(y)).append(" Td (")
                .append(escape(text)).append(") Tj ET\n");
    }

    public void fillRect(float x, float y, float width, float height, float[] rgb) {
        current.append(color(rgb, false))
                .append(num(x)).append(' ').append(num(y)).append(' ')
                .append(num(width)).append(' ').append(num(height)).append(" re f\n");
    }

    public void line(float x1, float y1, float x2, float y2, float lineWidth, float[] rgb) {
        current.append(color(rgb, true))
                .append(num(lineWidth)).append(" w ")
                .append(num(x1)).append(' ').append(num(y1)).append(" m ")
                .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
    }

    // ---------- Text measuring ----------

    /** Approximate width of the text in points. */
    public static float textWidth(String text, float size, boolean bold) {
        float units = 0;
        for (char c : sanitise(text).toCharArray()) {
            units += (c >= 32 && c <= 126) ? HELVETICA_WIDTHS[c - 32] : 556;
        }
        float width = units * size / 1000f;
        return bold ? width * BOLD_FACTOR : width;
    }

    /** Shortens the text with "..." so that it fits in maxWidth points. */
    public static String fit(String text, float size, boolean bold, float maxWidth) {
        String clean = sanitise(text);
        if (textWidth(clean, size, bold) <= maxWidth) {
            return clean;
        }
        String ellipsis = "...";
        String result = clean;
        while (!result.isEmpty() && textWidth(result + ellipsis, size, bold) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    // ---------- Output ----------

    /** Builds the finished PDF file. */
    public byte[] toBytes() {
        // Object numbers: 1 catalog, 2 page tree, 3 and 4 fonts, 5 info,
        // then for page i: page object 6 + 2i and its content stream 7 + 2i.
        int pageCount = pages.size();
        List<String> objects = new ArrayList<>();
        List<byte[]> streams = new ArrayList<>();

        objects.add("<< /Type /Catalog /Pages 2 0 R >>");

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(6 + 2 * i).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Title (BlueJ Telemetry Report) /Producer (COMP3607 BlueJ Telemetry) >>");

        for (int i = 0; i < pageCount; i++) {
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(PAGE_WIDTH) + " " + num(PAGE_HEIGHT)
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (7 + 2 * i) + " 0 R >>");
            objects.add(null); // placeholder, the content stream is added below
            streams.add(pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n%âãÏÓ\n");

        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n");
            String body = objects.get(i);
            if (body != null) {
                write(out, body + "\n");
            } else {
                byte[] stream = streams.get((i - 6) / 2);
                write(out, "<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream\n");
            }
            write(out, "endobj\n");
        }

        int xrefOffset = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n");
        write(out, "0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R /Info 5 0 R >>\n");
        write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return out.toByteArray();
    }

    // ---------- Helpers ----------

    /** Makes text safe inside a PDF string: keeps printable Latin-1, replaces the rest with '?'. */
    static String sanitise(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            boolean printable = (c >= 32 && c <= 126) || (c >= 161 && c <= 255);
            clean.append(printable ? c : (c == '\t' ? ' ' : '?'));
        }
        return clean.toString();
    }

    static String escape(String text) {
        return sanitise(text)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String color(float[] rgb, boolean stroke) {
        return num(rgb[0]) + " " + num(rgb[1]) + " " + num(rgb[2]) + (stroke ? " RG " : " rg ");
    }

    private static String num(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
