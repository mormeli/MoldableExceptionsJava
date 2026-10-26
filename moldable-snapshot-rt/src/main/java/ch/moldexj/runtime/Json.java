package ch.moldexj.runtime;

import java.util.List;

/** Just enough JSON writing for the snapshot; the plugin parses it with Gson. */
public final class Json {

    private Json() {
    }

    static String string(String value) {
        StringBuilder out = new StringBuilder();
        string(out, value);
        return out.toString();
    }

    static void string(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    static String strings(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i > 0 ? "," : "");
            string(out, values.get(i));
        }
        return out.append(']').toString();
    }

    /** Joins already written JSON values into an array. */
    static String array(List<String> json) {
        return "[" + String.join(",", json) + "]";
    }
}
