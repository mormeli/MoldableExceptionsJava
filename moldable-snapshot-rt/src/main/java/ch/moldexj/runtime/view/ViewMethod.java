package ch.moldexj.runtime.view;

import ch.moldexj.runtime.util.Reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A {@code @DebugView} method found in the debugged program, with its title, priority and parameter declarations. */
public final class ViewMethod {

    /** Lower priority first, then by title. */
    public static final Comparator<ViewMethod> ORDER = Comparator.<ViewMethod>comparingInt(v -> v.priority).thenComparing(v -> v.title);

    public final Method method;
    /** One per parameter; null for a method with parameters that no manifest declares. */
    public final ParameterDeclaration[] parameters;
    public final String title;
    public final int priority;

    public ViewMethod(Method method, ParameterDeclaration[] parameters, Annotation debugView) {
        this.method = method;
        this.parameters = parameters;
        String title = firstNonEmpty(Reflection.readString(debugView, "value"), Reflection.readString(debugView, "title"));
        this.title = title == null ? method.getName() : title;
        Object priority = Reflection.read(debugView, "priority");
        this.priority = priority instanceof Integer ? (Integer) priority : 100;
    }

    /**
     * Loads the declared classes and finds their methods. A method matches by name, static-ness and parameter count,
     * and must carry an annotation named DebugView.
     */
    static List<ViewMethod> resolve(List<ViewDeclaration> declarations, List<String> diagnostics) {
        List<ViewMethod> result = new ArrayList<>();
        Set<Method> added = new HashSet<>();
        for (ViewDeclaration declaration : declarations) {
            Class<?> owner = Reflection.loadClass(declaration.className);
            if (owner == null) {
                diagnostics.add("Views in " + declaration.className + " are not on the classpath of the debugged program; skipped.");
                continue;
            }
            Method[] methods;
            try {
                methods = owner.getDeclaredMethods();
            } catch (Throwable e) {
                continue;
            }
            for (Method m : methods) {
                Annotation debugView = Reflection.debugView(m);
                if (m.getName().equals(declaration.methodName) && Modifier.isStatic(m.getModifiers()) == declaration.isStatic
                        && m.getParameterCount() == declaration.parameters.size() && m.getReturnType() != void.class
                        && debugView != null && added.add(m)) {
                    result.add(new ViewMethod(m, declaration.parameters.toArray(new ParameterDeclaration[0]), debugView));
                }
            }
        }
        return result;
    }

    public boolean isStatic() {
        return Modifier.isStatic(method.getModifiers());
    }

    /** Static views apply through their {@code @Exception} parameter, instance views through their class. */
    boolean appliesTo(Throwable t) {
        if (!isStatic()) {
            return method.getDeclaringClass().isInstance(t);
        }
        if (parameters == null) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].kind == ParameterDeclaration.Kind.EXCEPTION) {
                return method.getParameterTypes()[i].isInstance(t);
            }
        }
        return false; // a static view without @Exception parameter is never shown
    }

    /** True for an instance view with the same name and arity: an override, or the same method found twice. */
    boolean sameInstanceView(Method other) {
        return !isStatic() && method.getName().equals(other.getName()) && method.getParameterCount() == other.getParameterCount();
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b != null && !b.isEmpty() ? b : null;
    }
}
