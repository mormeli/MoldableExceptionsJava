package dev.moldable.intellij.rt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs INSIDE THE DEBUGGED JVM. The IntelliJ plugin loads this class into the paused program with
 * {@code DebuggerUtilsImpl.invokeHelperMethod} (the mechanism IntelliJ uses for its own helpers) and calls
 * {@link #toJson(Throwable)}. It is the Java counterpart of the Visual Studio object source.
 *
 * <p>Constraints, because the class is defined from its bytes into an arbitrary JVM:
 * <ul>
 *   <li>compiled for Java 8, so it can be loaded into old JVMs;</li>
 *   <li>one class only: no nested or anonymous classes, no lambdas;</li>
 *   <li>no dependencies beyond the JDK: views are found by annotation <em>name</em> and read by duck typing.</li>
 * </ul>
 */
public final class MoldableSnapshot {

    static final int MAX_NODES = 32;
    static final int MAX_DEPTH = 12;
    static final int MAX_TEXT = 100_000;
    static final int MAX_ROWS = 1_000;
    static final int MAX_FRAMES = 200;

    private MoldableSnapshot() {
    }

    /** Entry point without external views. Never throws: problems end up in the snapshot as notes or error views. */
    public static String toJson(Throwable root) {
        return toJson(root, "");
    }

    /**
     * Entry point called by the plugin.
     *
     * @param externalViews views for exceptions you don't own: one {@code com.acme.SomeClass#methodName} per line, found
     *                      by the plugin in the project's sources. Each names static {@code @DebugView} methods that take
     *                      the exception as their only parameter, e.g. {@code parsePosition(DateTimeParseException e)}.
     */
    public static String toJson(Throwable root, String externalViews) {
        StringBuilder chain = new StringBuilder();
        List<String> notes = new ArrayList<String>();
        try {
            int[] count = new int[1];
            List<Method> external = resolveExternalViews(externalViews, notes);
            walk(root, "Exception", 0, chain, notes, new IdentityHashMap<Throwable, Boolean>(), count, external);
        } catch (Throwable t) {
            notes.add("Snapshot failed: " + describe(t));
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"chain\":[").append(chain).append("],\"notes\":[");
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            string(json, notes.get(i));
        }
        return json.append("]}").toString();
    }

    /** Loads the classes named in the spec and keeps their static, one-Throwable-parameter @DebugView methods. */
    private static List<Method> resolveExternalViews(String spec, List<String> notes) {
        List<Method> result = new ArrayList<Method>();
        if (spec == null || spec.isEmpty()) {
            return result;
        }
        for (String line : spec.split("\n")) {
            int hash = line.indexOf('#');
            if (hash <= 0) {
                continue;
            }
            String className = line.substring(0, hash).trim();
            String methodName = line.substring(hash + 1).trim();
            Class<?> owner = loadClass(className);
            if (owner == null) {
                notes.add("Views in " + className + " are not on the classpath of the debugged program; skipped.");
                continue;
            }
            Method[] declared;
            try {
                declared = owner.getDeclaredMethods();
            } catch (Throwable e) {
                continue;
            }
            for (Method m : declared) {
                Class<?>[] parameters = m.getParameterTypes();
                if (m.getName().equals(methodName) && Modifier.isStatic(m.getModifiers()) && parameters.length == 1
                        && Throwable.class.isAssignableFrom(parameters[0]) && m.getReturnType() != void.class
                        && findDebugView(m) != null && !result.contains(m)) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    /**
     * The helper's own loader is a child of the stopped frame's loader. When the debugger stops inside the JDK (for
     * example where java.time throws DateTimeParseException), that is the bootstrap loader, which cannot see application
     * classes, so the thread's context class loader (normally the application's) is tried as well.
     */
    private static Class<?> loadClass(String className) {
        ClassLoader own = MoldableSnapshot.class.getClassLoader();
        ClassLoader context = null;
        try {
            context = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ignored) {
            // ignore
        }
        ClassLoader[] loaders = {own, context};
        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(className, true, loader);
            } catch (Throwable ignored) {
                // try the next loader
            }
        }
        return null;
    }

    private static void walk(Throwable t, String label, int depth, StringBuilder chain, List<String> notes,
                             Map<Throwable, Boolean> seen, int[] count, List<Method> external) {
        if (t == null) {
            return;
        }
        if (seen.containsKey(t)) {
            notes.add(label + " refers back to an exception already shown (cycle); skipped.");
            return;
        }
        if (depth > MAX_DEPTH || count[0] >= MAX_NODES) {
            notes.add("Stopped at " + label + ": more than " + MAX_NODES + " exceptions or nesting deeper than " + MAX_DEPTH + ".");
            return;
        }
        seen.put(t, Boolean.TRUE);
        count[0]++;

        StringBuilder views = new StringBuilder();
        int custom = customViews(t, views, external);
        builtInViews(t, views);

        if (chain.length() > 0) {
            chain.append(',');
        }
        chain.append("{\"label\":");
        string(chain, label);
        chain.append(",\"depth\":").append(depth).append(",\"type\":");
        string(chain, t.getClass().getName());
        chain.append(",\"message\":");
        string(chain, safeMessage(t));
        chain.append(",\"moldable\":").append(custom > 0);
        chain.append(",\"views\":[").append(views).append("]}");

        Throwable cause = null;
        try {
            cause = t.getCause();
        } catch (Throwable ignored) {
            // a broken getCause() override must not break the debugger
        }
        if (cause != t) {
            walk(cause, "Cause", depth + 1, chain, notes, seen, count, external);
        }
        Throwable[] suppressed = new Throwable[0];
        try {
            suppressed = t.getSuppressed();
        } catch (Throwable ignored) {
            // ignore
        }
        for (int i = 0; i < suppressed.length; i++) {
            walk(suppressed[i], "Suppressed[" + i + "]", depth + 1, chain, notes, seen, count, external);
        }
    }

    /**
     * Finds the exception's own methods annotated with an annotation named DebugView, plus the external (static) views
     * whose parameter type matches it, invokes them, and appends the views sorted by priority.
     */
    private static int customViews(Throwable t, StringBuilder out, List<Method> external) {
        List<Method> methods = new ArrayList<Method>();
        List<String> titles = new ArrayList<String>();
        List<Integer> priorities = new ArrayList<Integer>();

        for (Method m : external) {
            if (m.getParameterTypes()[0].isInstance(t)) {
                add(m, findDebugView(m), methods, titles, priorities);
            }
        }

        for (Class<?> c = t.getClass(); c != null && c != Throwable.class && c != Object.class; c = c.getSuperclass()) {
            Method[] declared;
            try {
                declared = c.getDeclaredMethods();
            } catch (Throwable e) {
                continue;
            }
            for (Method m : declared) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0 || m.getReturnType() == void.class
                        || m.isBridge() || m.isSynthetic() || isOverridden(m, methods)) {
                    continue;
                }
                Annotation annotation = findDebugView(m);
                if (annotation == null) {
                    continue;
                }
                add(m, annotation, methods, titles, priorities);
            }
        }

        for (int i = 0; i < methods.size(); i++) {
            Method m = methods.get(i);
            Object result;
            String error = null;
            try {
                m.setAccessible(true);
                result = Modifier.isStatic(m.getModifiers()) ? m.invoke(null, t) : m.invoke(t);
            } catch (InvocationTargetException e) {
                result = null;
                error = describe(e.getCause() != null ? e.getCause() : e);
            } catch (Throwable e) {
                result = null;
                error = describe(e);
            }
            if (error != null) {
                view(out, titles.get(i), "error", true, priorities.get(i), "This view threw " + error, null, null, null, null, null, null);
            } else {
                convert(out, titles.get(i), priorities.get(i), result);
            }
        }
        return methods.size();
    }

    /** Inserts a view method sorted by priority, then title (no Comparator: that would need an extra class). */
    private static void add(Method m, Annotation annotation, List<Method> methods, List<String> titles, List<Integer> priorities) {
        String title = readString(annotation, "value");
        if (title == null || title.isEmpty()) {
            title = readString(annotation, "title");
        }
        Object priority = read(annotation, "priority");
        int p = priority instanceof Integer ? (Integer) priority : 100;
        String name = title == null || title.isEmpty() ? m.getName() : title;
        int at = 0;
        while (at < methods.size() && (priorities.get(at) < p || (priorities.get(at) == p && titles.get(at).compareTo(name) <= 0))) {
            at++;
        }
        methods.add(at, m);
        titles.add(at, name);
        priorities.add(at, p);
    }

    private static boolean isOverridden(Method m, List<Method> alreadyFound) {
        for (Method found : alreadyFound) {
            if (!Modifier.isStatic(found.getModifiers()) && found.getName().equals(m.getName())) {
                return true; // a subclass already contributed this (zero-argument) method
            }
        }
        return false;
    }

    private static Annotation findDebugView(Method m) {
        Annotation[] annotations;
        try {
            annotations = m.getAnnotations();
        } catch (Throwable e) {
            return null;
        }
        for (Annotation a : annotations) {
            if ("DebugView".equals(a.annotationType().getSimpleName())) {
                return a;
            }
        }
        return null;
    }

    /** Converts a view result by duck typing: String, or an object with kind()/text()/expected()/... accessors. */
    private static void convert(StringBuilder out, String title, int priority, Object result) {
        if (result == null) {
            view(out, title, "text", true, priority, "(the view returned null)", null, null, null, null, null, null);
            return;
        }
        if (result instanceof CharSequence) {
            view(out, title, "text", true, priority, result.toString(), null, null, null, null, null, null);
            return;
        }
        String kind = readString(result, "kind");
        if (kind == null) {
            kind = readString(result, "getKind");
        }
        kind = kind == null ? "" : kind.toLowerCase();
        if ("text".equals(kind)) {
            view(out, title, "text", true, priority, readString(result, "text"), null, null, null, null, null, null);
        } else if ("diff".equals(kind)) {
            view(out, title, "diff", true, priority, null, readString(result, "expected"), readString(result, "actual"),
                    readString(result, "expectedLabel"), readString(result, "actualLabel"), null, null);
        } else if ("table".equals(kind)) {
            List<String> columns = strings(read(result, "columns"));
            List<List<String>> rows = new ArrayList<List<String>>();
            Object rowsValue = read(result, "rows");
            int total = 0;
            for (Object row : items(rowsValue)) {
                total++;
                if (rows.size() < MAX_ROWS) {
                    rows.add(strings(row));
                }
            }
            if (total > MAX_ROWS) {
                List<String> marker = new ArrayList<String>();
                marker.add("… " + (total - MAX_ROWS) + " more rows not shown");
                rows.add(marker);
            }
            List<String> rowStyles = strings(read(result, "rowStyles"));
            StringBuilder extra = new StringBuilder();
            if (!rowStyles.isEmpty()) {
                extra.append(",\"rowStyles\":");
                stringArray(extra, rowStyles.size() > rows.size() ? rowStyles.subList(0, rows.size()) : rowStyles);
            }
            view(out, title, "table", true, priority, null, null, null, null, null, columns, rows, extra.toString());
        } else if ("highlight".equals(kind)) {
            String text = readString(result, "text");
            text = text == null ? "" : truncate(text);
            StringBuilder extra = new StringBuilder(",\"spans\":[");
            int written = 0;
            for (Object span : items(read(result, "spans"))) {
                Object start = read(span, "start");
                Object end = read(span, "end");
                if (!(start instanceof Integer) || !(end instanceof Integer) || written >= MAX_ROWS) {
                    continue;
                }
                int from = Math.max(0, Math.min((Integer) start, text.length()));
                int to = Math.max(from, Math.min((Integer) end, text.length()));
                if (written++ > 0) {
                    extra.append(',');
                }
                extra.append("{\"start\":").append(from).append(",\"end\":").append(to).append(",\"style\":");
                string(extra, readString(span, "style"));
                extra.append(",\"tooltip\":");
                string(extra, readString(span, "tooltip"));
                extra.append('}');
            }
            extra.append("],\"caption\":");
            string(extra, readString(result, "caption"));
            view(out, title, "highlight", true, priority, text, null, null, null, null, null, null, extra.toString());
        } else {
            // Unknown shape: fall back to toString(), so a quick prototype view still shows something.
            view(out, title, "text", true, priority, safeToString(result), null, null, null, null, null, null);
        }
    }

    private static void builtInViews(Throwable t, StringBuilder out) {
        StringBuilder details = new StringBuilder();
        details.append("Type:      ").append(t.getClass().getName()).append('\n');
        details.append("Message:   ").append(safeMessage(t)).append('\n');
        try {
            String localized = t.getLocalizedMessage();
            if (localized != null && !localized.equals(t.getMessage())) {
                details.append("Localized: ").append(localized).append('\n');
            }
        } catch (Throwable ignored) {
            // ignore
        }
        try {
            details.append("toString:  ").append(t.toString()).append('\n');
        } catch (Throwable ignored) {
            // ignore
        }
        ClassLoader loader = t.getClass().getClassLoader();
        details.append("Loader:    ").append(loader == null ? "bootstrap" : safeToString(loader)).append('\n');
        view(out, "Details", "text", false, 1000, details.toString(), null, null, null, null, null, null);

        StringBuilder trace = new StringBuilder();
        StackTraceElement[] frames;
        try {
            frames = t.getStackTrace();
        } catch (Throwable e) {
            frames = new StackTraceElement[0];
        }
        if (frames.length == 0) {
            trace.append("(no stack trace)");
        }
        for (int i = 0; i < frames.length && i < MAX_FRAMES; i++) {
            trace.append("at ").append(frames[i]).append('\n');
        }
        if (frames.length > MAX_FRAMES) {
            trace.append("… ").append(frames.length - MAX_FRAMES).append(" more frames");
        }
        view(out, "Stack trace", "text", false, 1001, trace.toString(), null, null, null, null, null, null);

        // Fields declared by the exception's own classes (Throwable's internals are already shown above).
        List<String> columns = new ArrayList<String>();
        columns.add("Field");
        columns.add("Value");
        columns.add("Declared in");
        List<List<String>> rows = new ArrayList<List<String>>();
        for (Class<?> c = t.getClass(); c != null && c != Throwable.class && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable e) {
                continue;
            }
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                String value;
                try {
                    f.setAccessible(true);
                    value = safeToString(f.get(t));
                } catch (Throwable e) {
                    value = "(not accessible: " + e.getClass().getSimpleName() + ")";
                }
                List<String> row = new ArrayList<String>();
                row.add(f.getName());
                row.add(value);
                row.add(c.getSimpleName());
                rows.add(row);
            }
        }
        if (!rows.isEmpty()) {
            view(out, "Fields", "table", false, 1002, null, null, null, null, null, columns, rows);
        }
    }

    private static void view(StringBuilder out, String title, String kind, boolean custom, int priority, String text,
                             String expected, String actual, String expectedLabel, String actualLabel,
                             List<String> columns, List<List<String>> rows) {
        view(out, title, kind, custom, priority, text, expected, actual, expectedLabel, actualLabel, columns, rows, null);
    }

    /** @param extra raw JSON members (",\"a\":1") appended to the view object, or null */
    private static void view(StringBuilder out, String title, String kind, boolean custom, int priority, String text,
                             String expected, String actual, String expectedLabel, String actualLabel,
                             List<String> columns, List<List<String>> rows, String extra) {
        if (out.length() > 0) {
            out.append(',');
        }
        out.append("{\"title\":");
        string(out, title);
        out.append(",\"kind\":");
        string(out, kind);
        out.append(",\"custom\":").append(custom).append(",\"priority\":").append(priority);
        if (text != null) {
            out.append(",\"text\":");
            string(out, truncate(text));
        }
        if (expected != null || actual != null) {
            out.append(",\"expected\":");
            string(out, truncate(expected == null ? "" : expected));
            out.append(",\"actual\":");
            string(out, truncate(actual == null ? "" : actual));
            out.append(",\"expectedLabel\":");
            string(out, expectedLabel == null ? "Expected" : expectedLabel);
            out.append(",\"actualLabel\":");
            string(out, actualLabel == null ? "Actual" : actualLabel);
        }
        if (columns != null) {
            out.append(",\"columns\":");
            stringArray(out, columns);
        }
        if (rows != null) {
            out.append(",\"rows\":[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                stringArray(out, rows.get(i));
            }
            out.append(']');
        }
        if (extra != null) {
            out.append(extra);
        }
        out.append('}');
    }

    // ---- reflection helpers ----

    private static Object read(Object target, String accessor) {
        try {
            Method m = target.getClass().getMethod(accessor);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String readString(Object target, String accessor) {
        Object value = read(target, accessor);
        return value == null ? null : safeToString(value);
    }

    private static List<Object> items(Object value) {
        List<Object> list = new ArrayList<Object>();
        if (value instanceof Iterable) {
            for (Object o : (Iterable<?>) value) {
                list.add(o);
            }
        } else if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                list.add(Array.get(value, i));
            }
        }
        return list;
    }

    private static List<String> strings(Object value) {
        List<String> list = new ArrayList<String>();
        if (value instanceof Iterable || (value != null && value.getClass().isArray())) {
            for (Object o : items(value)) {
                list.add(o == null ? "" : safeToString(o));
            }
        } else if (value != null) {
            list.add(safeToString(value));
        }
        return list;
    }

    private static String safeMessage(Throwable t) {
        try {
            String message = t.getMessage();
            return message == null ? "" : message;
        } catch (Throwable e) {
            return "(getMessage() threw " + e.getClass().getName() + ")";
        }
    }

    private static String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable e) {
            return "(toString() threw " + e.getClass().getName() + ")";
        }
    }

    private static String describe(Throwable e) {
        StringWriter writer = new StringWriter();
        writer.append(e.getClass().getName()).append(": ").append(safeMessage(e)).append("\n\n");
        try {
            e.printStackTrace(new PrintWriter(writer));
        } catch (Throwable ignored) {
            // ignore
        }
        return writer.toString();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "\n… truncated (" + text.length() + " characters in total)";
    }

    // ---- minimal JSON writing ----

    private static void stringArray(StringBuilder out, List<String> values) {
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            string(out, values.get(i));
        }
        out.append(']');
    }

    private static void string(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
            return;
        }
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
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
}
