package ch.moldexj.data;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Where a {@link DebuggerDataProvider} reads its data: the paused program at the exception of the snapshot. */
public interface ProviderContext {

    @NotNull EvaluationContextImpl getEvaluationContext();

    /** The exception the snapshot is taken of, the root of the chain. */
    @NotNull ObjectReference getException();

    /**
     * The thread that threw the exception, if the debugger is stopped at that throw (an exception breakpoint or the
     * uncaught stop); then its frames are the stack of the throw. Null when the views are opened by hand elsewhere.
     */
    @Nullable ThreadReferenceProxyImpl getThrowThread();
}
