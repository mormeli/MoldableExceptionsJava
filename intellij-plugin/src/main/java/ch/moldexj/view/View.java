package ch.moldexj.view;

import ch.moldexj.view.request.ParameterSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A {@code @DebugView} method with parameters, as the IDE knows it from sources or a library manifest.
 *
 * @param className the binary class name ({@code a.B$C})
 */
public record View(@NotNull String className, @NotNull String methodName, boolean isStatic, @NotNull List<Parameter> parameters) {

    public View {
        parameters = List.copyOf(parameters);
    }

    /** Identifies the method like the helper and the manifest do: by class, name and parameter count. */
    public @NotNull String key() {
        return className + "#" + methodName + "/" + parameters.size();
    }

    /** The exception type the view is shown for: its {@code @Exception} parameter, or its class for instance views. */
    public @Nullable String exceptionType() {
        if (!isStatic) {
            return className;
        }
        for (Parameter parameter : parameters) {
            if (parameter.source() instanceof ParameterSource.ThrownException) {
                return parameter.typeName();
            }
        }
        return null;
    }

    public boolean needsDebuggerData() {
        return parameters.stream().anyMatch(p -> p.source() instanceof ParameterSource.Provided);
    }
}
