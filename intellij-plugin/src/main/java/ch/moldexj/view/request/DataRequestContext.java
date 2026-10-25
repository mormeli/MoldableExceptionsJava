package ch.moldexj.view.request;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.sun.jdi.event.ExceptionEvent;

public interface DataRequestContext {
    EvaluationContext getEvaluationContext();
    ExceptionEvent getExceptionEvent();
}
