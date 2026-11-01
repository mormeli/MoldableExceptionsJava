package ch.moldexj.runtime;

import ch.moldexj.runtime.view.ParameterDeclaration;
import ch.moldexj.runtime.view.ViewCatalog;
import ch.moldexj.runtime.view.ViewMethod;
import ch.moldexj.runtime.mold.Json;
import ch.moldexj.runtime.mold.ViewRunner;
import ch.moldexj.runtime.plan.Binding;
import ch.moldexj.runtime.plan.ChainWalker;
import ch.moldexj.runtime.plan.PlannedNode;
import ch.moldexj.runtime.plan.PlannedView;
import ch.moldexj.runtime.plan.RequestTable;
import ch.moldexj.runtime.util.Safe;
import ch.moldexj.runtime.value.InjectedValues;
import ch.moldexj.runtime.value.Unavailable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs INSIDE THE DEBUGGED JVM. The IntelliJ plugin defines the classes of this module in the paused program from their
 * bytes (see {@code HelperLoader}) and calls two methods.
 *
 * <ol>
 *   <li>{@link #plan(Throwable)} finds the views of the exception chain and says which debugger data they need.</li>
 *   <li>The plugin resolves that data (stack frames, ...) and calls {@link #mold(MoldPlan, byte[], Object[])}, which runs
 *       the planned views and returns the snapshot as JSON.</li>
 * </ol>
 *
 * <p>Constraints, because the classes are defined into an arbitrary JVM: Java 8 bytecode, and no dependencies beyond the
 * JDK. Views are found by annotation <em>name</em> and read by duck typing, so the program needs no library either.
 *
 * <p>Where views come from:
 * <ul>
 *   <li>the exception's own {@code @DebugView} methods without parameters, found by reflection;</li>
 *   <li>the manifests of all jars and output folders on the classpath, for every view with parameters and every static
 *       view ({@code catalog.ClasspathManifests}).</li>
 * </ul>
 */
public final class ExceptionMolder {

    private static final String ONLY_AT_ROOT = "This view needs data from the debugger, which is only available for the"
            + " exception the debugger stopped at, not for its causes or suppressed exceptions.";
    private static final String NOT_DECLARED = "This view has parameters, but no view manifest declares them. Compile"
            + " the project with moldable-views-processor.";

    private ExceptionMolder() {
    }

    /***
     * Finds the views of the chain and the debugger data they need. Never throws: problems end up in the notes.
     * @param root The throwable to collect thew MoldPlan for.
     * @return The {@see MoldPlan} which is used to create the final view.
     */
    public static MoldPlan plan(Throwable root) {
        List<String> diagnostics = new ArrayList<>();
        List<PlannedNode> nodes = Collections.emptyList();
        RequestTable requests = new RequestTable();
        try {
            // Collect all available views.
            List<ViewMethod> declared = ViewCatalog.declared(diagnostics);

            // Collect the exception / cause / suppression chain.
            // Then iterate over the collected nodes (depth first tree) and find available views.
            // In the process we collect the required data to render said view.
            nodes = new ChainWalker(root, diagnostics).walk();
            for (PlannedNode node : nodes) {
                for (ViewMethod view : ViewCatalog.viewsFor(node.exception, declared)) {
                    node.views.add(new PlannedView(view, bindings(view, node.isRoot, requests)));
                }
            }
        } catch (Throwable t) {
            diagnostics.add("Finding the views failed: " + Safe.describe(t));
        }
        return new MoldPlan(root, nodes, diagnostics, requests);
    }

    /**
     * Runs the planned views. Never throws: problems end up in the snapshot as notes or error views.
     *
     * @param data    the values for the plan's requests, written by {@code DebuggeeMarshaller} ({@link InjectedValues}),
     *                or null if there are none
     * @param handles the live objects referenced from {@code data}, or null
     */
    public static String mold(MoldPlan plan, byte[] data, Object[] handles) {
        List<String> diagnostics = new ArrayList<>();
        List<String> chain = new ArrayList<>();
        try {
            diagnostics.addAll(plan.notes);

            List<Object> values = InjectedValues.read(data, handles);
            if (values.size() != plan.requestCount) {
                diagnostics.add("The debugger sent " + values.size() + " values for " + plan.requestCount + " requests.");
                values = Collections.nCopies(plan.requestCount, new Unavailable("The debugger did not send this value."));
            }
            ViewRunner runner = new ViewRunner(values);
            for (PlannedNode node : plan.nodes) {
                chain.add(runner.render(node));
            }
        } catch (Throwable t) {
            diagnostics.add("Snapshot failed: " + Safe.describe(t));
        }
        return "{\"chain\":" + Json.array(chain) + ",\"notes\":" + Json.strings(diagnostics) + "}";
    }

    private static Binding[] bindings(ViewMethod view, boolean isRoot, RequestTable requests) {
        int count = view.method.getParameterCount();
        Binding[] bindings = new Binding[count];
        for (int i = 0; i < count; i++) {
            ParameterDeclaration parameter = view.parameters == null ? null : view.parameters[i];
            if (parameter == null) {
                bindings[i] = Binding.missing(NOT_DECLARED);
                continue;
            }
            switch (parameter.kind) {
                case EXCEPTION:
                    bindings[i] = Binding.exception();
                    break;
                case DEBUGGER:
                    bindings[i] = isRoot ? Binding.value(requests.indexOf(parameter.annotations)) : Binding.missing(ONLY_AT_ROOT);
                    break;
                default:
                    bindings[i] = Binding.missing(parameter.reason);
            }
        }
        return bindings;
    }
}
