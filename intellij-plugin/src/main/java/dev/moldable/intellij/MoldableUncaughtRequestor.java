package dev.moldable.intellij;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.ExceptionRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stops the debugger at the throw of an uncaught exception whose chain is moldable, without any breakpoint.
 *
 * <p>When a debug session attaches, the plugin registers its own exception request (uncaught only, all exception
 * types) with IntelliJ's request manager, the same mechanism breakpoints use. The JVM reports "uncaught" at the throw,
 * so the program is still alive there. This requestor then takes the snapshot, like a breakpoint condition, and
 * stops only if an exception in the chain has views: its own {@code @DebugView} methods, or static views in the
 * project (e.g. for PatternSyntaxException). Other uncaught exceptions behave exactly as without the plugin.
 */
final class MoldableUncaughtRequestor implements FilteredRequestor {

    static final String ENABLED_KEY = "moldable.exceptions.stopOnUncaught";

    private static final Logger LOG = Logger.getInstance(MoldableUncaughtRequestor.class);

    private final Project project;

    private MoldableUncaughtRequestor(Project project) {
        this.project = project;
    }

    static boolean isEnabled() {
        return PropertiesComponent.getInstance().getBoolean(ENABLED_KEY, true);
    }

    /** Call on the debugger manager thread, once the VM is attached. */
    static void install(Project project, DebugProcessImpl process) {
        MoldableUncaughtRequestor requestor = new MoldableUncaughtRequestor(project);
        // null reference type = every exception type; notifyCaught = false, notifyUncaught = true
        ExceptionRequest request = process.getRequestsManager().createExceptionRequest(requestor, null, false, true);
        process.getRequestsManager().enableRequest(request);
        LOG.info("Moldable Exceptions: stopping at uncaught moldable exceptions");
    }

    @Override
    public String getSuspendPolicy() {
        return DebuggerSettings.SUSPEND_ALL;
    }

    @Override
    public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, @Nullable LocatableEvent event) {
        if (!(event instanceof ExceptionEvent) || !isEnabled()) {
            return false;
        }
        SuspendContextImpl suspendContext = action.getSuspendContext();
        if (suspendContext == null) {
            return false;
        }
        try {
            // Same as a breakpoint condition: evaluate in the paused program, decide whether to stop.
            EvaluationContextImpl context = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
            ExceptionSnapshot snapshot = SnapshotLoader.load(context, ((ExceptionEvent) event).exception(),
                    ExternalViewFinder.find(project));
            LOG.info("Moldable Exceptions: uncaught " + (snapshot.chain.isEmpty() ? "?" : snapshot.chain.get(0).type)
                    + ", moldable=" + snapshot.hasMoldable());
            return snapshot.hasMoldable();
        } catch (Exception | LinkageError e) {
            LOG.warn("Moldable Exceptions: could not check the uncaught exception", e);
            return false; // never stop the program because of a plugin problem
        }
    }
}
