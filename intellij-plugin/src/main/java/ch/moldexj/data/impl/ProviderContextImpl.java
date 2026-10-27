package ch.moldexj.data.impl;

import ch.moldexj.data.ProviderContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ProviderContextImpl(
        @NotNull EvaluationContextImpl evaluationContext,
        @NotNull ObjectReference exception,
        @Nullable ThreadReferenceProxyImpl throwThread) implements ProviderContext {

    @Override
    public @NotNull EvaluationContextImpl getEvaluationContext() {
        return evaluationContext;
    }

    @Override
    public @NotNull ObjectReference getException() {
        return exception;
    }

    @Override
    public @Nullable ThreadReferenceProxyImpl getThrowThread() {
        return throwThread;
    }
}
