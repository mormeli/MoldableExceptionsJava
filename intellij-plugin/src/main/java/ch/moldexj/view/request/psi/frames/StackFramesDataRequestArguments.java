package ch.moldexj.view.request.psi.frames;

import ch.moldexj.view.request.DataRequestArguments;

public final class StackFramesDataRequestArguments extends DataRequestArguments {
    private final int depth;


    public StackFramesDataRequestArguments(Class<?> processor,  int depth) {
        super(processor);
        this.depth  = depth;
    }


    public int getDepth() {
        return depth;
    }
}
