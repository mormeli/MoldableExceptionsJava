package ch.moldexj.debuggee;

import ch.moldexj.data.AnnotationData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Debugger data a view in the debugged program asks for: all annotations of its parameter, as the view manifest has
 * them. Which one a provider understands is decided in {@link ch.moldexj.data.DebuggerDataProviders}.
 */
public record DataRequest(@NotNull List<AnnotationData> annotations) {

    public DataRequest {
        annotations = List.copyOf(annotations);
    }
}
