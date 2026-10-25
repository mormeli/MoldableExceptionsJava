package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;

/** Where the value of a view parameter comes from. */
public sealed interface ParameterSource {

    /** The exception the view is shown for ({@code @Exception}). Bound in the debugged JVM, nothing to resolve here. */
    record ThrownException() implements ParameterSource {
    }

    /** Data the debugger provides: resolved by the processor before the snapshot, then sent to the debugged JVM. */
    record Provided(@NotNull DataRequestArguments arguments, @NotNull DataRequestProcessor<?> processor) implements ParameterSource {
    }

    /** The parameter cannot be filled; the view shows the reason instead of running. */
    record Unsupported(@NotNull String reason) implements ParameterSource {
    }
}
