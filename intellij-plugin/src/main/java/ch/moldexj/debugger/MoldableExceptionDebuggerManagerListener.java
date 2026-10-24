package ch.moldexj.debugger;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the moldable views automatically when the debugger stops on an exception breakpoint and the exception's
 * chain contains an exception that declares its own views. This is the Glamorous Toolkit behavior: the exception
 * changes what the debugger shows.
 *
 * <p>It also installs {@link UncaughtMoldableExceptionRequestor}, which makes the debugger stop at uncaught moldable
 * exceptions without any breakpoint. The JVM decides at the throw whether an exception will be caught, so that stop
 * is at the throw itself, with the program alive, and the snapshot can run there.
 */
public final class MoldableExceptionDebuggerManagerListener implements XDebuggerManagerListener {

    private static final Logger LOG = Logger.getInstance(MoldableExceptionDebuggerManagerListener.class);

    public static final String ENABLED_KEY = "moldable.exceptions.autoOpen";

    private final Project project;

    public MoldableExceptionDebuggerManagerListener(Project project) {
        this.project = project;
    }

    @Override
    public void processStarted(@NotNull XDebugProcess debugProcess) {
        LOG.info("Moldable Exceptions: debug process started: " + debugProcess.getClass().getName());

        if (!(debugProcess instanceof JavaDebugProcess)) {
            return;
        }

        DebugProcessImpl debuggeeProcess = ((JavaDebugProcess) debugProcess).getDebuggerSession().getProcess();
        // The listener lives as long as the debug process; it is dropped with it.
        debuggeeProcess.addDebugProcessListener(new MoldableDebugProcessListener(project, debuggeeProcess));

        LOG.info("Moldable Exceptions: listening for exception stops");
    }
}
