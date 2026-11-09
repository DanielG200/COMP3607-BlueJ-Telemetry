package tt.edu.uwi.comp3607.export;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Turns Java values (maps, lists, strings, numbers, booleans)
 * into JSON text.
 **/
public final class JsonWriter {

    private JsonWriter() {
        // utility class
    }

    public static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

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

    private static void pad(StringBuilder out, int indent) {
        out.append("  ".repeat(indent));
    }
}