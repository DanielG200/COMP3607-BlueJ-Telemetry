package tt.edu.uwi.comp3607.export;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Turns normal Java values (maps, lists, strings, numbers and so on) into JSON
 * text.
 *
 * We wrote this ourselves instead of using a library like Gson. A BlueJ
 * extension has to be a single jar, and putting extra libraries inside it is a
 * hassle, so a small writer of our own was simpler. This class only knows how
 * to write JSON. Deciding what goes in the file is JsonExporter's job (Single
 * Responsibility).
 *
 * What it supports:
 * - null, booleans and numbers (NaN and infinity become null, since JSON
 *   doesn't allow them)
 * - strings, with quotes, backslashes, new lines and other special characters
 *   escaped properly
 * - {@link Instant}, written as text like "2026-11-09T10:00:00Z"
 * - enums, written using their name, e.g. "COMPILE_ERROR"
 * - {@link Map} as a JSON object, and any {@link Collection} as a JSON array
 * - anything else is written as text using its toString()
 *
 * The output is pretty-printed with 2 spaces per level so it's easy to read.
 * Everything here is static, so just call {@link #toJson(Object)}.
 *
 * @author Kirese Tasha Maharaj
 * @see JsonExporter
 */

public final class JsonWriter {

/** Private so nobody makes a JsonWriter object, since everything here is static. */
    private JsonWriter() {
        // utility class
    }

/**
 * Returns the JSON text for a value. This is the only method other classes need.
 * For example, a map with "line" -> 7 becomes:
 * <pre>{@code
 * {
 *   "line": 7
 * }
 * }</pre>
 *
 * @param value the value to turn into JSON (can be {@code null})
 * @return the JSON text
 */
    public static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }


/**
 * Works out what kind of value this is and writes it the right way. It calls
 * itself for values inside maps and lists, which is how nested JSON works.
 *
 * @param value  the value to write
 * @param out    where the JSON text is being built up
 * @param indent how many levels deep we are, used for spacing
 */
    private static void write(Object value, StringBuilder out, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            out.append(Double.isFinite(number) ? String.valueOf(number) : "null");
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof Instant instant) {
            writeString(instant.toString(), out);
        } else if (value instanceof Enum<?> enumValue) {
            writeString(enumValue.name(), out);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, out, indent);
        } else if (value instanceof Collection<?> list) {
            writeArray(list, out, indent);
        } else {
            writeString(value.toString(), out);
        }
    }

/**
 * Writes a map as a JSON object, with one key per line in the same order as
 * the map. An empty map is written as {@code {}}.
 *
 * @param map    the map to write
 * @param out    where the JSON text is being built up
 * @param indent how many levels deep we are
 */
    private static void writeObject(Map<?, ?> map, StringBuilder out, int indent) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            pad(out, indent + 1);
            writeString(String.valueOf(entry.getKey()), out);
            out.append(": ");
            write(entry.getValue(), out, indent + 1);
            if (++i < map.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        pad(out, indent);
        out.append('}');
    }


/**
 * Writes a list as a JSON array, with one item per line. An empty list is
 * written as {@code []}.
 *
 * @param list   the list to write
 * @param out    where the JSON text is being built up
 * @param indent how many levels deep we are
 */
    private static void writeArray(Collection<?> list, StringBuilder out, int indent) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        int i = 0;
        for (Object item : list) {
            pad(out, indent + 1);
            write(item, out, indent + 1);
            if (++i < list.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        pad(out, indent);
        out.append(']');
    }

/**
 * Writes text as a JSON string in double quotes, escaping anything that would
 * break the JSON. For example, a double quote becomes a backslash and a quote,
 * and a new line becomes backslash-n. This matters because compile error
 * messages often have quotes in them, like ';' expected.
 *
 * @param text the text to write
 * @param out  where the JSON text is being built up
 */
    static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }


/**
 * Adds 2 spaces for each level of indent, so nested values line up nicely.
 *
 * @param out    where the JSON text is being built up
 * @param indent how many levels deep we are
 */
    private static void pad(StringBuilder out, int indent) {
        out.append("  ".repeat(indent));
    }
}