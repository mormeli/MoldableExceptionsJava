package dev.moldable.intellij;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debugger-thread command: snapshot the exception, then open the dialog on the UI thread.
 * Used both by the context-menu action and by the automatic opening at exception breakpoints.
 */
final class SnapshotCommand extends SuspendContextCommandImpl {

    private static final Logger LOG = Logger.getInstance(SnapshotCommand.class);

    private final Project project;
    private final @Nullable EvaluationContextImpl givenContext;
    private final ObjectReference exception;
    private final String origin;
    private final boolean automatic;

    /**
     * @param givenContext evaluation context of a Variables-view value, or null to build one for the current frame
     * @param automatic    true when opened by an exception breakpoint: then only moldable chains open a dialog, and
     *                     failures are logged instead of shown
     */
    SnapshotCommand(@NotNull Project project, @NotNull SuspendContextImpl suspendContext, @Nullable EvaluationContextImpl givenContext,
                    @NotNull ObjectReference exception, @NotNull String origin, boolean automatic) {
        super(suspendContext);
        this.project = project;
        this.givenContext = givenContext;
        this.exception = exception;
        this.origin = origin;
        this.automatic = automatic;
    }

    @Override
    public PrioritizedTask.Priority getPriority() {
        // Let IntelliJ fill frames and variables first.
        return automatic ? PrioritizedTask.Priority.LOW : PrioritizedTask.Priority.NORMAL;
    }

    @Override
    public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        ExceptionSnapshot snapshot;
        try {
            if (!SnapshotLoader.isThrowable(exception)) {
                report("The selected value is not a Throwable.");
                return;
            }
            EvaluationContextImpl context = givenContext != null
                    ? givenContext
                    : new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
            snapshot = SnapshotLoader.load(context, exception, ExternalViewFinder.find(project));
        } catch (Exception | LinkageError e) {
            // LinkageError: e.g. a library (Gson) not visible to the plugin, or the helper class failing to load.
            LOG.warn("Moldable Exceptions: snapshot failed", e);
            report("Could not take a snapshot of the exception: " + e.getMessage());
            return;
        }

        LOG.info("Moldable Exceptions: snapshot has " + snapshot.chain.size() + " exception(s), moldable=" + snapshot.hasMoldable());
        if (automatic && !snapshot.hasMoldable()) {
            return; // plain exceptions keep IntelliJ's normal behavior
        }
        ApplicationManager.getApplication().invokeLater(
                () -> MoldableViewsDialog.open(project, snapshot, origin, automatic),
                project.getDisposed());
    }

    @Override
    protected void commandCancelled() {
        // The program was resumed before the snapshot could be taken.
        LOG.info("Moldable Exceptions: snapshot cancelled (the program was resumed)");
        if (!automatic) {
            report("The program was resumed before the snapshot could be taken.");
        }
    }

    private void report(String message) {
        if (automatic) {
            LOG.info("Moldable Exceptions: " + message);
            return;
        }
        ApplicationManager.getApplication().invokeLater(
                () -> Messages.showWarningDialog(project, message, "Moldable Exceptions"),
                project.getDisposed());
    }
}
