package ch.moldexj.debuggee;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A {@code MoldPlan} in the debugged JVM, protected from its garbage collector until {@link #close()}: nothing in the
 * program references it, so the GC could take it between {@code plan} and {@code mold}.
 *
 * @param reference the plan in the debugged JVM, passed back to {@code mold}
 * @param moldable  true if an exception of the chain has views of its own
 * @param requests  the debugger data the views need; the values go to {@code mold} in this order
 */
public record DebuggeePlan(
        @NotNull ObjectReference reference,
        boolean moldable,
        @NotNull List<DataRequest> requests) implements AutoCloseable {

    @Override
    public void close() {
        try {
            reference.enableCollection();
        } catch (RuntimeException ignored) {
            // the VM is gone; nothing to release
        }
    }
}
