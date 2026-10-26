package ch.moldexj.view.annotation;

import ch.moldexj.view.context.DebugFrame;
import ch.moldexj.view.context.DebugStack;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asks the debugger for the stack of the thread that threw the exception, with {@code this}, the arguments and the
 * local variables of each frame. That is more than {@link Throwable#getStackTrace()}, which only has class, method,
 * file and line.
 *
 * <pre>{@code
 * @DebugView("Locals")
 * static String locals(@Exception IllegalStateException e, @StackFrames(depth = 5) DebugStack stack) { ... }
 * }</pre>
 *
 * <p>The parameter type may be {@link DebugStack} (or any interface of the same shape), {@link java.util.Map} or
 * {@link Object}. The stack only exists while the debugger is stopped at the throw of this exception; elsewhere (a
 * cause of the stopped exception, or a view opened by hand at a breakpoint) the view shows why it is not available.
 *
 * <p>The plugin reads this annotation from the sources, or from the manifest that {@code moldable-views-processor}
 * writes, so it does not have to be retained at runtime.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface StackFrames {

    /** How many frames to read, starting at the throw site. */
    int depth();

    /**
     * Also read the fields of {@code this} in each frame ({@link DebugFrame#fields()}). The debugger reads them, so
     * this includes private fields of JDK classes, which a view cannot read by reflection since Java 9. Costs one more
     * read per frame.
     */
    boolean fields() default false;
}
