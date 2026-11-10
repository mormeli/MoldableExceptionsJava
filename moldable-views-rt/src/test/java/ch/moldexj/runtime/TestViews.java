package ch.moldexj.runtime;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.annotation.Exception;
import ch.moldexj.view.annotation.StackFrames;
import ch.moldexj.view.context.DebugStack;

/** Static views for {@link ViewedException}, found through the manifest the processor writes for the test classes. */
final class TestViews {

    private TestViews() {
    }

    /** Needs only the exception, so it runs without a debugger. */
    @DebugView(value = "Message", priority = 20)
    static String message(@Exception ViewedException e) {
        return "message: " + e.getMessage();
    }

    /** Needs the debugger, so without one it must not run. */
    @DebugView(value = "Stack", priority = 30)
    static String stack(@Exception ViewedException e, @StackFrames(depth = 3) DebugStack stack) {
        return "frames: " + stack.frames().size();
    }
}
