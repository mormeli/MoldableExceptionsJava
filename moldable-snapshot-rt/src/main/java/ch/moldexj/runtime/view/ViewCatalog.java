package ch.moldexj.runtime.view;

import ch.moldexj.runtime.util.Reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The views of the debugged program: those of the classpath manifests, read once per helper (the plugin defines the
 * helper once per debug session and class loader), and the {@code @DebugView} methods of an exception's class.
 */
public final class ViewCatalog {

    private static List<ViewMethod> declared;
    private static List<String> declaredNotes;

    private ViewCatalog() {
    }

    /** The views of all manifests on the classpath. Problems found while reading them are added to {@code notes}. */
    public static synchronized List<ViewMethod> declared(List<String> diagnostics) {
        if (declared == null) {
            List<String> readNotes = new ArrayList<>();
            declared = ViewMethod.resolve(ClasspathManifests.read(readNotes), readNotes);
            declaredNotes = readNotes;
        }
        diagnostics.addAll(declaredNotes);
        return declared;
    }

    /**
     * The views that apply to the exception: declared ones, plus the {@code @DebugView} methods of its class found by
     * reflection. Those need a declaration only if they have parameters.
     */
    public static List<ViewMethod> viewsFor(Throwable t, List<ViewMethod> declared) {
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
                views.add(new ViewMethod(m, m.getParameterCount() == 0 ? new ParameterDeclaration[0] : null, debugView));
            }
        }
        return views;
    }
}
