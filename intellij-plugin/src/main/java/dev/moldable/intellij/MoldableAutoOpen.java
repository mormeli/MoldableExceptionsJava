package dev.moldable.intellij;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opens the moldable views automatically when the debugger stops on an exception breakpoint and the exception's
 * chain contains an exception that declares its own views. This is the Glamorous Toolkit behavior: the exception
 * changes what the debugger shows.
 *
 * <p>It also installs {@link MoldableUncaughtRequestor}, which makes the debugger stop at uncaught moldable
 * exceptions without any breakpoint. The JVM decides at the throw whether an exception will be caught, so that stop
 * is at the throw itself, with the program alive, and the snapshot can run there.
 */
public final class MoldableAutoOpen implements XDebuggerManagerListener {

    private static final Logger LOG = Logger.getInstance(MoldableAutoOpen.class);

    static final String ENABLED_KEY = "moldable.exceptions.autoOpen";

    private final Project project;

    public MoldableAutoOpen(Project project) {
        this.project = project;
    }

    static boolean isEnabled() {
        return PropertiesComponent.getInstance().getBoolean(ENABLED_KEY, true);
    }

    @Override
    public void processStarted(@NotNull XDebugProcess debugProcess) {
        LOG.info("Moldable Exceptions: debug process started: " + debugProcess.getClass().getName());
        if (!(debugProcess instanceof JavaDebugProcess)) {
            return;
        }
        DebugProcessImpl process = ((JavaDebugProcess) debugProcess).getDebuggerSession().getProcess();
        AtomicBoolean installed = new AtomicBoolean();
        // The listener lives as long as the debug process; it is dropped with it.
        process.addDebugProcessListener(new DebugProcessListener() {
            @Override
            public void processAttached(@NotNull DebugProcess attached) {
                installStopOnUncaught(process, installed);
            }

            @Override
            public void paused(@NotNull SuspendContext suspendContext) {
                LOG.info("Moldable Exceptions: paused (auto-open " + (isEnabled() ? "on" : "off") + ")");
                if (isEnabled() && suspendContext instanceof SuspendContextImpl) {
                    try {
                        onPaused((SuspendContextImpl) suspendContext);
                    } catch (Throwable e) {
                        LOG.warn("Moldable Exceptions: could not handle the pause", e);
                    }
                }
            }
        });
        if (process.isAttached()) {
            installStopOnUncaught(process, installed); // the VM attached before we got here
        }
        LOG.info("Moldable Exceptions: listening for exception stops");
    }

    /** Registers the plugin's own "uncaught exceptions" request; no breakpoint settings are touched. */
    private void installStopOnUncaught(DebugProcessImpl process, AtomicBoolean installed) {
        process.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
            if (installed.compareAndSet(false, true)) {
                try {
                    MoldableUncaughtRequestor.install(project, process);
                } catch (Exception e) {
                    LOG.warn("Moldable Exceptions: could not register the uncaught-exception request", e);
                }
            }
        });
    }

    /** Called on the debugger manager thread. */
    private void onPaused(SuspendContextImpl suspendContext) {
        EventSet events = suspendContext.getEventSet();
        if (events == null) {
            LOG.info("Moldable Exceptions: no event set (not a breakpoint stop)");
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
        LOG.info("Moldable Exceptions: not an exception stop: " + events);
    }
}
