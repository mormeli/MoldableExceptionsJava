package ch.moldexj.view.request.impl;

import ch.moldexj.view.request.DataRequestContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record DataRequestContextImpl(
        @NotNull EvaluationContextImpl evaluationContext,
        @NotNull ObjectReference exception,
        @Nullable ThreadReferenceProxyImpl throwThread) implements DataRequestContext {

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
