package ch.moldexj.view.context;

import java.util.List;

/** One frame of a {@link DebugStack}. */
public interface DebugFrame {

    /** Position in the stack, 0 is the throw site. */
    int index();

    String className();

    String methodName();

    /** The source file, or null if the class has no debug information. */
    String fileName();

    /** The line number, or -1 if unknown. */
    int line();

    /** The live {@code this} object of the frame; null in static methods. */
    Object thisObject();

    /** The arguments first, then the local variables visible at the current line. */
    List<DebugLocal> locals();

    /** The argument or local variable with this name, or null. */
    DebugLocal local(String name);

    /** The fields of {@code this}, inherited ones included; empty unless requested with {@code @StackFrames(fields = true)}. */
    List<DebugLocal> fields();

    /** The field of {@code this} with this name, or null. */
    DebugLocal field(String name);

    /** False if the class was compiled without local variable information: then only arguments named arg0, arg1, ... */
    boolean hasDebugInfo();
}
