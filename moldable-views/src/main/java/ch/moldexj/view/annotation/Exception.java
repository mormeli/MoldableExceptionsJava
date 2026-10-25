package ch.moldexj.view.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the parameter of a static {@code @DebugView} method that receives the exception the view is shown for. The
 * view is shown for every exception in the chain whose type matches the parameter type.
 *
 * <pre>{@code
 * @DebugView("Parse position")
 * static String parsePosition(@Exception DateTimeParseException e) { ... }
 * }</pre>
 *
 * <p>Views declared on the exception class itself don't need it: there the exception is {@code this}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface Exception {
}
