package ch.moldexj.view.request.psi.exception;

import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.DataRequestContext;
import ch.moldexj.view.request.DataRequestProcessor;

public class PsiExceptionDataRequestProcessor implements DataRequestProcessor {
    @Override
    public Object process(DataRequestArguments request, DataRequestContext context) {
        return context.getExceptionEvent().exception();
    }
}
