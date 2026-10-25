package ch.moldexj.view.request.psi.frames;

import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.DataRequestContext;
import ch.moldexj.view.request.DataRequestProcessor;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;

import java.util.List;

public class PsiStackFrameDataRequestProcessor implements DataRequestProcessor {
    @Override
    public Object process(DataRequestArguments request, DataRequestContext context) {
        int depth = ((StackFramesDataRequestArguments) request).getDepth();
        SuspendContextImpl suspendContext = (SuspendContextImpl) context.getEvaluationContext().getSuspendContext();
        ThreadReferenceProxyImpl thread = suspendContext.getThread();
        if (thread == null) {
            return List.of(); // e.g. a "pause all" with no particular thread
        }
        try {
            List<StackFrameProxyImpl> frames = thread.frames(); // frame 0 = top of the stack, the throw site
            return frames.subList(0, Math.min(depth, frames.size()));
        } catch (EvaluateException e) {
            throw new IllegalStateException("Could not read the stack of " + thread, e);
        }
    }
}
