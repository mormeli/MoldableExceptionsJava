package ch.moldexj.view.request.impl;

import ch.moldexj.view.request.DataRequestContext;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.sun.jdi.event.ExceptionEvent;

public final class DataRequestContextImpl implements DataRequestContext {
    private final EvaluationContext evaluationContext;
    private final ExceptionEvent exceptionEvent;


    public DataRequestContextImpl(EvaluationContext evaluationContext, ExceptionEvent exceptionEvent) {
        this.evaluationContext  = evaluationContext;
        this.exceptionEvent     = exceptionEvent;
    }

    /**
     * @return
     */
    @Override
    public EvaluationContext getEvaluationContext() {
        return evaluationContext;
    }

    /**
     * @return
     */
    @Override
    public ExceptionEvent getExceptionEvent() {
        return exceptionEvent;
    }
}
