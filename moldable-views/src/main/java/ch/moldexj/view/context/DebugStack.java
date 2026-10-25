package ch.moldexj.view.context;

import ch.moldexj.view.annotation.StackFrames;

import java.util.List;

/**
 * The stack of the thread that threw the exception, as the debugger saw it at the throw. Requested with
 * {@link StackFrames}.
 *
 * <p>The plugin passes a proxy that implements this interface, so an application can also declare its own interface
 * with the same method names instead of depending on this library.
 */
public interface DebugStack {

    /** The frames, {@code [0]} is the throw site. */
    List<DebugFrame> frames();

    /** The first frame of the given method, or null. */
    DebugFrame frameOf(String className, String methodName);

    /** True if the stack had more frames than were read. */
    boolean truncated();
}
