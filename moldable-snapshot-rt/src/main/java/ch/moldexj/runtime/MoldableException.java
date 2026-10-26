package ch.moldexj.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs INSIDE THE DEBUGGED JVM. The IntelliJ plugin defines the classes of this module in the paused program from their
 * bytes (see {@code SnapshotLoader}) and calls {@link #handleException(Throwable, String, byte[], Object[])}. It is the Java
 * counterpart of the Visual Studio object source.
 *
 * <p>Constraints, because the classes are defined into an arbitrary JVM: Java 8 bytecode, and no dependencies beyond the
 * JDK. Views are found by annotation <em>name</em> and read by duck typing, so the program needs no library either.
 *
 * <p>Where views come from:
 * <ul>
 *   <li>the exception's own {@code @DebugView} methods, found by reflection ({@link CustomViews});</li>
 *   <li>the view spec sent by the plugin: every view the IDE knows, with how to fill each parameter ({@link ViewSpec});</li>
 *   <li>the manifests of all jars on the classpath, for views the IDE does not know ({@link ClasspathManifests}).</li>
 * </ul>
 */
public final class MoldableException {

    private MoldableException() {
    }

    /** Entry point without views from the IDE. Never throws: problems end up in the snapshot as notes or error views. */
    public static String handleException(Throwable root) {
        return handleException(root, "", null, null);
    }

    /**
     * Entry point called by the plugin.
     *
     * @param spec     the views the IDE knows ({@link ViewSpec})
     * @param injected the values for {@code bind ... value k} ({@link InjectedValues}), or null
     * @param handles  the live objects referenced from {@code injected}, or null
     */
    public static String handleException(
            Throwable root,
            String spec,
            byte[] injected,
            Object[] handles) {
        List<String> notes = new ArrayList<>();
        List<String> chain = new ArrayList<>();
        try {
            List<Object> values = InjectedValues.read(injected, handles);
            List<ViewMethod> views = ViewMethod.resolve(declarations(spec, notes), notes);
            chain = new ChainWalker(root, new CustomViews(views, values), notes).walk();
        } catch (Throwable t) {
            notes.add("Snapshot failed: " + Safe.describe(t));
        }
        return "{\"chain\":" + Json.array(chain) + ",\"notes\":" + Json.strings(notes) + "}";
    }

    /** The plugin's views, then those of the classpath manifests that the plugin did not send. */
    private static List<ViewDeclaration> declarations(String spec, List<String> notes) {
        List<ViewDeclaration> declarations = ViewSpec.parse(spec);
        Set<String> known = new HashSet<>();
        declarations.forEach(declaration -> known.add(declaration.key()));
        for (ViewDeclaration declaration : ClasspathManifests.read(notes)) {
            if (known.add(declaration.key())) {
                declarations.add(declaration);
            }
        }
        return declarations;
    }
}
