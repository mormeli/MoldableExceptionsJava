package ch.moldexj.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fills view parameters with data only the debugger has, e.g. {@code @StackFrames}. Implementations are stateless and
 * registered in {@link DebuggerDataProviders}.
 *
 * @param <A> the arguments of a request, read from the annotation; must define {@code equals} and {@code hashCode}
 *            (records do): equal requests are resolved once per snapshot
 */
public interface DebuggerDataProvider<A> {

    /**
     * The arguments of a request from one parameter annotation, or null if the annotation is not this provider's.
     *
     * @throws IllegalArgumentException if the declaration is wrong (e.g. a depth of 0); the message is shown in place
     *                                  of the view
     */
    @Nullable A parse(@NotNull AnnotationData annotation);

    /**
     * Resolves a request in the paused program, on the debugger manager thread. Return the data as a tree that can be
     * sent to the debugged JVM (see {@link ProvidedValue.Available}), never as IDE objects: a {@code StackFrameProxyImpl}
     * only exists in the IDE. Prefer plain JDI reads; a method invocation in the debugged JVM makes the stack frames of
     * the thread invalid.
     */
    @NotNull ProvidedValue provide(@NotNull ProviderContext context, @NotNull A arguments);
}
