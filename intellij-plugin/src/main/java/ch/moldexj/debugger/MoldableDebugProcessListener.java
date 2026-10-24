package ch.moldexj.debugger;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.request.ExceptionRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class MoldableDebugProcessListener implements DebugProcessListener {
    private static final Logger LOG = Logger.getInstance(MoldableDebugProcessListener.class);

    private static final String ENABLED_KEY = "moldable.exceptions.autoOpen";

    public static boolean isEnabled() {
        return PropertiesComponent.getInstance().getBoolean(ENABLED_KEY, true);
    }

    private final Project project;
    private final DebugProcessImpl debuggeeProcess;
    private final AtomicBoolean installed;

    public MoldableDebugProcessListener(Project project, DebugProcessImpl debuggee) {
        this.project            = project;
        this.debuggeeProcess = debuggee;
        this.installed          = new AtomicBoolean();
    }

    @Override
    public void processAttached(@NotNull DebugProcess attached) {
        LOG.info("processAttached - Attaching DebugProcess");

        debuggeeProcess.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
            if (installed.compareAndSet(false, true)) {
                try {
                    installRequestor();
                } catch (Exception e) {
                    LOG.warn("processAttached - Could not register the uncaught-exception request", e);
                }
            }
        });
        LOG.info("processAttached - Done");
    }

    @Override
    public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
        LOG.info("processDetached -  Detaching DebugProcess");
    }

    @Override
    public void paused(@NotNull SuspendContext suspendContext) {
        LOG.info("paused - paused (auto-open " + (isEnabled() ? "on" : "off") + ")");
        if (isEnabled() && suspendContext instanceof SuspendContextImpl) {
            try {
                onPaused((SuspendContextImpl) suspendContext);
            } catch (Throwable e) {
                LOG.warn("paused - could not handle the pause", e);
            }
        }
        LOG.info("paused - done");
    }

    /** Called on the debugger manager thread. */
    private void onPaused(SuspendContextImpl suspendContext) {
        EventSet events = suspendContext.getEventSet();
        if (events == null) {
            LOG.info("onPaused - no event set (not a breakpoint stop)");
            return;
        }
        for (Event event : events) {
            if (event instanceof ExceptionEvent) {
                ExceptionEvent exceptionEvent = (ExceptionEvent) event;
                String origin = exceptionEvent.catchLocation() == null ? "uncaught" : "thrown";
                LOG.info("Moldable Exceptions: exception stop (" + origin + "), taking a snapshot");
                suspendContext.getManagerThread().schedule(
                        new SnapshotCommand(project, suspendContext, null, exceptionEvent.exception(), origin, true));
                return;
            }
        }
        LOG.info("onPaused - not an exception stop: " + events);
    }

    /***
     * Adds the {@see UncaughtMoldableExceptionRequestor} to the debuggees request manager.
     */
    private void installRequestor() {
        LOG.info("installRequestor - Installing exception requestor");
        UncaughtMoldableExceptionRequestor requestor = new UncaughtMoldableExceptionRequestor(project);
        // null reference type = every exception type; notifyCaught = false, notifyUncaught = true
        ExceptionRequest request = debuggeeProcess.getRequestsManager().createExceptionRequest(requestor, null, false, true);
        debuggeeProcess.getRequestsManager().enableRequest(request);
        LOG.info("installRequestor - Done");
    }
}
