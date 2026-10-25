package ch.moldexj.view;

import ch.moldexj.view.request.ParameterSource;
import org.jetbrains.annotations.NotNull;

/**
 * A parameter of a view method.
 *
 * @param typeName the erased type as the JVM names it, e.g. {@code java.util.regex.PatternSyntaxException}
 */
public record Parameter(int position, @NotNull String typeName, @NotNull ParameterSource source) {
}
