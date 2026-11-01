package ch.moldexj.runtime.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Duck typing and class loading in the debugged program; nothing here throws. */
public final class Reflection {

    private Reflection() {
    }

    /**
     * The helper's own loader is a child of the stopped frame's loader. When the debugger stops inside the JDK (for
     * example where java.time throws DateTimeParseException), that is the bootstrap loader, which cannot see
     * application classes, so the thread's context class loader (normally the application's) is tried as well.
     */
    public static List<ClassLoader> classLoaders() {
        ClassLoader context;
        try {
            context = Thread.currentThread().getContextClassLoader();
        } catch (Throwable e) {
            context = null;
        }
        return Stream.of(Reflection.class.getClassLoader(), context).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
    }

    public static Class<?> loadClass(String className) {
        for (ClassLoader loader : classLoaders()) {
            try {
                return Class.forName(className, true, loader);
            } catch (Throwable ignored) {
                // try the next loader
            }
        }
        return null;
    }

    /** The annotation named {@code DebugView}, from moldable-views or the project's own. */
    public static Annotation debugView(Method m) {
        try {
            return Arrays.stream(m.getAnnotations())
                    .filter(a -> a.annotationType().getSimpleName().equals("DebugView"))
                    .findFirst().orElse(null);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Calls the public, parameterless method {@code accessor()}; null if there is none or it throws. */
    public static Object read(Object target, String accessor) {
        try {
            Method m = target.getClass().getMethod(accessor);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable e) {
            return null;
        }
    }

    public static String readString(Object target, String accessor) {
        Object value = read(target, accessor);
        return value == null ? null : Safe.toString(value);
    }

    /** The elements of an Iterable or array; empty for anything else. */
    public static List<Object> items(Object value) {
        List<Object> list = new ArrayList<>();
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

    /** The elements as text, or the value itself as a single text. */
    public static List<String> strings(Object value) {
        if (value instanceof Iterable || (value != null && value.getClass().isArray())) {
            return items(value).stream().map(o -> o == null ? "" : Safe.toString(o)).collect(Collectors.toList());
        }
        List<String> list = new ArrayList<>();
        if (value != null) {
            list.add(Safe.toString(value));
        }
        return list;
    }
}
