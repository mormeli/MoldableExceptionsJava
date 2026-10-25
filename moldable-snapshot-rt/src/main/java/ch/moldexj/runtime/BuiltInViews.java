package ch.moldexj.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Details, Stack trace and Fields: shown for every exception, after its own views. */
final class BuiltInViews {

    private BuiltInViews() {
    }

    static List<String> render(Throwable t) {
        List<String> views = new ArrayList<>();
        views.add(new ViewJson("Details", "text", false, 1000).text(details(t)).toString());
        views.add(new ViewJson("Stack trace", "text", false, 1001).text(stackTrace(t)).toString());
        List<List<String>> fields = fields(t);
        if (!fields.isEmpty()) {
            views.add(new ViewJson("Fields", "table", false, 1002)
                    .table(Arrays.asList("Field", "Value", "Declared in"), fields).toString());
        }
        return views;
    }

    private static String details(Throwable t) {
        StringBuilder details = new StringBuilder();
        details.append("Type:      ").append(t.getClass().getName()).append('\n');
        details.append("Message:   ").append(Safe.message(t)).append('\n');
        try {
            String localized = t.getLocalizedMessage();
            if (localized != null && !localized.equals(t.getMessage())) {
                details.append("Localized: ").append(localized).append('\n');
            }
        } catch (Throwable ignored) {
            // leave it out
        }
        details.append("toString:  ").append(Safe.toString(t)).append('\n');
        ClassLoader loader = t.getClass().getClassLoader();
        details.append("Loader:    ").append(loader == null ? "bootstrap" : Safe.toString(loader)).append('\n');
        return details.toString();
    }

    private static String stackTrace(Throwable t) {
        StackTraceElement[] frames;
        try {
            frames = t.getStackTrace();
        } catch (Throwable e) {
            frames = new StackTraceElement[0];
        }
        if (frames.length == 0) {
            return "(no stack trace)";
        }
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < frames.length && i < Limits.FRAMES; i++) {
            trace.append("at ").append(frames[i]).append('\n');
        }
        if (frames.length > Limits.FRAMES) {
            trace.append("… ").append(frames.length - Limits.FRAMES).append(" more frames");
        }
        return trace.toString();
    }

    /** Fields declared by the exception's own classes (Throwable's internals are already shown above). */
    private static List<List<String>> fields(Throwable t) {
        List<List<String>> rows = new ArrayList<>();
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
                    value = Safe.toString(f.get(t));
                } catch (Throwable e) {
                    value = "(not accessible: " + e.getClass().getSimpleName() + ")";
                }
                rows.add(Arrays.asList(f.getName(), value, c.getSimpleName()));
            }
        }
        return rows;
    }
}
