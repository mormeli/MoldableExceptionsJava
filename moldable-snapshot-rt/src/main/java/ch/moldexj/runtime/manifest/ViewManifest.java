package ch.moldexj.runtime.manifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The view manifest at {@value #PATH}: every {@code @DebugView} method of a compilation unit, with the annotations of
 * its parameters. {@code DebugViewProcessor} (moldable-views-processor) writes it into each jar or output folder; the IntelliJ plugin reads it
 * from library jars, and the helper in the debugged JVM reads all copies on the classpath.
 *
 * <p>The format is line based, so the helper (a single Java 8 class) can parse it without a library:
 *
 * <pre>
 * # moldexj views 1
 * view ch.acme.Views locals static
 * param 0 java.lang.IllegalStateException ch.moldexj.view.Exception
 * param 1 ch.moldexj.view.DebugStack ch.moldexj.view.StackFrames depth=i:5
 * </pre>
 *
 * <ul>
 *   <li>{@code view <binary class name> <method> static|instance}</li>
 *   <li>{@code param <index> <erased parameter type> <annotation> <attributes...>}: one line per annotation of the
 *       parameter, or a single line with {@code -} as annotation if it has none.</li>
 *   <li>Attributes are {@code name=tag:value}, with the tags {@code z} boolean, {@code i} int, {@code j} long,
 *       {@code d} double, {@code s} string, {@code e} enum constant name, {@code t} class name. Defaults are included.
 *       Other values (arrays, nested annotations) are left out.</li>
 *   <li>Tokens are separated by single spaces; {@code %}, space, {@code =}, tab and line breaks inside a token are
 *       written as {@code %XX}.</li>
 * </ul>
 *
 * Methods are identified by name and parameter count; overloads with the same count are not distinguished.
 */
public final class ViewManifest {

    public static final String PATH = "META-INF/moldexj/views";
    public static final String HEADER = "# moldexj views 1";
    public static final String NO_ANNOTATION = "-";

    private ViewManifest() {
    }

    public static final class ViewEntry {
        public final String className;
        public final String methodName;
        public final boolean isStatic;
        public final List<ParamEntry> params = new ArrayList<ParamEntry>();

        public ViewEntry(String className, String methodName, boolean isStatic) {
            this.className = className;
            this.methodName = methodName;
            this.isStatic = isStatic;
        }

        public String key() {
            return className + "#" + methodName + "/" + params.size();
        }
    }

    public static final class ParamEntry {
        public final int index;
        public final String type;
        public final List<AnnotationEntry> annotations = new ArrayList<AnnotationEntry>();

        public ParamEntry(int index, String type) {
            this.index = index;
            this.type = type;
        }
    }

    public static final class AnnotationEntry {
        public final String name;
        /** Boolean, Integer, Long, Double, String; enum constants and class literals as their names (String). */
        public final Map<String, Object> attributes;

        public AnnotationEntry(String name, Map<String, Object> attributes) {
            this.name = name;
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
        }
    }

    // ---- reading ----

    /** Parses a manifest. Unknown or broken lines are skipped, so newer manifests stay readable. */
    public static List<ViewEntry> read(Reader reader) throws IOException {
        List<ViewEntry> views = new ArrayList<ViewEntry>();
        BufferedReader lines = new BufferedReader(reader);
        ViewEntry current = null;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] tokens = line.split(" ");
            if (tokens[0].equals("view") && tokens.length >= 4) {
                current = new ViewEntry(decode(tokens[1]), decode(tokens[2]), tokens[3].equals("static"));
                views.add(current);
            } else if (tokens[0].equals("param") && tokens.length >= 4 && current != null) {
                int index;
                try {
                    index = Integer.parseInt(tokens[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                ParamEntry param = current.params.isEmpty() ? null : current.params.get(current.params.size() - 1);
                if (param == null || param.index != index) {
                    param = new ParamEntry(index, decode(tokens[2]));
                    current.params.add(param);
                }
                String annotation = decode(tokens[3]);
                if (!annotation.equals(NO_ANNOTATION)) {
                    Map<String, Object> attributes = new LinkedHashMap<String, Object>();
                    for (int i = 4; i < tokens.length; i++) {
                        int eq = tokens[i].indexOf('=');
                        if (eq > 0) {
                            Object value = decodeValue(tokens[i].substring(eq + 1));
                            if (value != null) {
                                attributes.put(decode(tokens[i].substring(0, eq)), value);
                            }
                        }
                    }
                    param.annotations.add(new AnnotationEntry(annotation, attributes));
                }
            }
        }
        return views;
    }

    private static Object decodeValue(String token) {
        int colon = token.indexOf(':');
        if (colon != 1) {
            return null;
        }
        String text = decode(token.substring(2));
        try {
            switch (token.charAt(0)) {
                case 'z':
                    return Boolean.valueOf(text);
                case 'i':
                    return Integer.valueOf(text);
                case 'j':
                    return Long.valueOf(text);
                case 'd':
                    return Double.valueOf(text);
                case 's':
                case 'e':
                case 't':
                    return text;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- writing ----

    public static void write(Writer out, List<ViewEntry> views) throws IOException {
        out.write(HEADER);
        out.write('\n');
        for (ViewEntry view : views) {
            out.write("view " + encode(view.className) + " " + encode(view.methodName) + " "
                    + (view.isStatic ? "static" : "instance") + "\n");
            for (ParamEntry param : view.params) {
                String prefix = "param " + param.index + " " + encode(param.type) + " ";
                if (param.annotations.isEmpty()) {
                    out.write(prefix + NO_ANNOTATION + "\n");
                }
                for (AnnotationEntry annotation : param.annotations) {
                    StringBuilder line = new StringBuilder(prefix).append(encode(annotation.name));
                    for (Map.Entry<String, Object> attribute : annotation.attributes.entrySet()) {
                        String value = encodeValue(attribute.getValue());
                        if (value != null) {
                            line.append(' ').append(encode(attribute.getKey())).append('=').append(value);
                        }
                    }
                    out.write(line.append('\n').toString());
                }
            }
        }
    }

    /** Strings are written with tag {@code s}; use {@link #enumConstant} and {@link #classLiteral} for the others. */
    private static String encodeValue(Object value) {
        if (value instanceof Tagged) {
            return ((Tagged) value).tag + ":" + encode(((Tagged) value).text);
        } else if (value instanceof Boolean) {
            return "z:" + value;
        } else if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "i:" + value;
        } else if (value instanceof Long) {
            return "j:" + value;
        } else if (value instanceof Double || value instanceof Float) {
            return "d:" + value;
        } else if (value instanceof String || value instanceof Character) {
            return "s:" + encode(value.toString());
        }
        return null;
    }

    /** An attribute value to write as an enum constant; it is read back as its name. */
    public static Object enumConstant(String name) {
        return new Tagged('e', name);
    }

    /** An attribute value to write as a class literal; it is read back as the class name. */
    public static Object classLiteral(String className) {
        return new Tagged('t', className);
    }

    private static final class Tagged {
        final char tag;
        final String text;

        Tagged(char tag, String text) {
            this.tag = tag;
            this.text = text;
        }
    }

    /** Escapes %, space, =, tab and line breaks as %XX, so the text is one token. */
    public static String encode(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' || c == ' ' || c == '=' || c == '\t' || c == '\n' || c == '\r') {
                out.append('%').append(String.format("%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String decode(String token) {
        if (token.indexOf('%') < 0) {
            return token;
        }
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '%' && i + 2 < token.length()) {
                try {
                    out.append((char) Integer.parseInt(token.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (RuntimeException ignored) {
                    // keep the % as it is
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
