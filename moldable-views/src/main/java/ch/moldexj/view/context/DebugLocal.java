package ch.moldexj.view.context;

/** An argument, local variable or field of {@code this} in a {@link DebugFrame}. */
public interface DebugLocal {

    String name();

    /** The declared type, e.g. {@code java.util.List} or {@code int}. */
    String typeName();

    boolean isArgument();

    /**
     * The live value: the object itself, or a boxed primitive. Only valid while the program is paused; calling methods
     * on it can change the program's state.
     */
    Object value();

    /** A safe {@code toString()} of the value, shortened if it is very long. */
    String text();
}
