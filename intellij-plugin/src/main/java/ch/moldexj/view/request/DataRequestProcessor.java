package ch.moldexj.view.request;

import org.jetbrains.annotations.NotNull;

/**
 * Resolves a data request in the paused program, on the debugger manager thread, before the views run.
 *
 * <p>Return the data as a tree that can be sent to the debugged JVM (see {@link ProvidedValue.Available}), never as
 * IDE objects: a {@code StackFrameProxyImpl} only exists in the IDE. Prefer plain JDI reads. A method invocation in
 * the debugged JVM is possible but makes the stack frames of the thread invalid.
 */
public interface DataRequestProcessor<A extends DataRequestArguments> {

    @NotNull ProvidedValue process(@NotNull A arguments, @NotNull DataRequestContext context);
}
