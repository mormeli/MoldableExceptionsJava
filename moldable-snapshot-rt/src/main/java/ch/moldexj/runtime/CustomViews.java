package ch.moldexj.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The views an exception declares or that are declared for it, run with their parameters. The data from the
 * debugger (stack frames, ...) belongs to the exception the debugger stopped at, the root of the chain.
 */
final class CustomViews {

    private final List<ViewMethod> declared;
    private final List<Object> values;

    CustomViews(List<ViewMethod> declared, List<Object> values) {
        this.declared = declared;
        this.values = values;
    }

    /** The views of one exception of the chain, as JSON, sorted by priority. */
    List<String> render(Throwable t, boolean isRoot) {
        List<ViewMethod> views = viewsFor(t);
        views.sort(ViewMethod.ORDER);
        List<String> json = new ArrayList<>();
        for (ViewMethod view : views) {
            json.add(run(view, t, isRoot));
        }
        return json;
    }

    /**
     * Declared views (spec and manifests) that apply to the exception, plus the {@code @DebugView} methods of its class
     * found by reflection. Those need a declaration only if they have parameters.
     */
    private List<ViewMethod> viewsFor(Throwable t) {
        List<ViewMethod> views = new ArrayList<>();
        for (ViewMethod view : declared) {
            if (view.appliesTo(t)) {
                views.add(view);
            }
        }
        for (Class<?> c = t.getClass(); c != null && c != Throwable.class && c != Object.class; c = c.getSuperclass()) {
            Method[] methods;
            try {
                methods = c.getDeclaredMethods();
            } catch (Throwable e) {
                continue;
            }
            for (Method m : methods) {
                Annotation debugView = Reflection.debugView(m);
                if (debugView == null || Modifier.isStatic(m.getModifiers()) || m.getReturnType() == void.class
                        || m.isBridge() || m.isSynthetic() || views.stream().anyMatch(v -> v.sameInstanceView(m))) {
                    continue; // a subclass, or a declaration, already contributed it
                }
                views.add(new ViewMethod(m, m.getParameterCount() == 0 ? new Binding[0] : null, debugView));
            }
        }
        return views;
    }

    private String run(ViewMethod view, Throwable t, boolean isRoot) {
        Object[] arguments;
        try {
            arguments = arguments(view, t, isRoot);
        } catch (CannotRun e) {
            return ViewJson.text(view.title, view.priority, "unavailable", e.getMessage()).toString();
        }
        try {
            view.method.setAccessible(true);
            Object result = view.method.invoke(view.isStatic() ? null : t, arguments);
            return ResultConverter.convert(view.title, view.priority, result);
        } catch (InvocationTargetException e) {
            return error(view, e.getCause() != null ? e.getCause() : e);
        } catch (Throwable e) {
            return error(view, e);
        }
    }

    private Object[] arguments(ViewMethod view, Throwable t, boolean isRoot) throws CannotRun {
        if (view.bindings == null) {
            throw new CannotRun("This view has parameters, but the plugin found no declaration that says how to fill"
                    + " them. Compile the project with moldable-views-processor, or keep the sources in the IntelliJ project.");
        }
        Class<?>[] types = view.method.getParameterTypes();
        Object[] arguments = new Object[view.bindings.length];
        for (int i = 0; i < arguments.length; i++) {
            Binding binding = view.bindings[i];
            switch (binding.kind) {
                case EXCEPTION:
                    arguments[i] = t;
                    break;
                case MISSING:
                    throw new CannotRun(binding.reason);
                case VALUE:
                    if (!isRoot) {
                        throw new CannotRun("This view needs data from the debugger, which is only available for the"
                                + " exception the debugger stopped at, not for its causes or suppressed exceptions.");
                    }
                    Object value = binding.valueIndex < values.size() ? values.get(binding.valueIndex) : null;
                    if (value instanceof Unavailable) {
                        throw new CannotRun(((Unavailable) value).reason);
                    }
                    try {
                        arguments[i] = ValueAdapter.adapt(value, types[i], view.method.getGenericParameterTypes()[i]);
                    } catch (IllegalArgumentException e) {
                        throw new CannotRun("Parameter " + i + ": " + e.getMessage());
                    }
                    break;
            }
        }
        return arguments;
    }

    private static String error(ViewMethod view, Throwable e) {
        return ViewJson.text(view.title, view.priority, "error", "This view threw " + Safe.describe(e)).toString();
    }

    /** A view whose parameters cannot be filled; the message says why. */
    private static final class CannotRun extends Exception {
        CannotRun(String reason) {
            super(reason, null, false, false);
        }
    }
}
