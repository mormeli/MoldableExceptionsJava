package dev.moldable.exceptions;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameterless instance method of an exception as a debugger view, the Java counterpart of a
 * {@code <gtView>} method in Glamorous Toolkit's moldable exceptions.
 *
 * <p>The method may return a {@link DebugViews.View} (see {@link DebugViews}) or a plain {@link String}, which is
 * shown as text. It may be private: the exception decides how it wants to be debugged, not its callers.
 *
 * <p>Views run inside the paused JVM when the IntelliJ plugin opens them, so keep them fast and free of side effects.
 *
 * <p>The plugin finds this annotation by its simple name ({@code DebugView}) and reads {@code title()} and
 * {@code priority()} by reflection, so a project can also declare its own annotation with the same shape instead of
 * depending on this library.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DebugView {

    /** Tab title shown in the debugger. */
    String value();

    /** Ordering of the view; lower comes first. The lowest-priority view opens by default. */
    int priority() default 100;
}
