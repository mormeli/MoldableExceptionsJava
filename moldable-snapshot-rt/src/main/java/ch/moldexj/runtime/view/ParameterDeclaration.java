package ch.moldexj.runtime.view;

import ch.moldexj.runtime.view.ViewManifest.AnnotationEntry;

import java.util.Collections;
import java.util.List;

/** What the manifest says about one parameter of a view; {@link ch.moldexj.runtime.ExceptionMolder#plan} turns it into a {@link ch.moldexj.runtime.plan.Binding}. */
public final class ParameterDeclaration {

    public enum Kind {
        /** {@code @Exception}: the exception the view is shown for. */
        EXCEPTION,
        /** Any other annotation: data only the debugger has, requested from the plugin by {@link #annotations}. */
        DEBUGGER,
        /** Cannot be filled: the view shows {@link #reason} instead of running. */
        MISSING
    }

    public final Kind kind;
    public final List<AnnotationEntry> annotations;
    public final String reason;

    private ParameterDeclaration(Kind kind, List<AnnotationEntry> annotations, String reason) {
        this.kind = kind;
        this.annotations = annotations;
        this.reason = reason;
    }

    static ParameterDeclaration exception() {
        return new ParameterDeclaration(Kind.EXCEPTION, Collections.<AnnotationEntry>emptyList(), null);
    }

    /** @param annotations all annotations of the parameter: the plugin decides which one it can provide */
    static ParameterDeclaration debugger(List<AnnotationEntry> annotations) {
        return new ParameterDeclaration(Kind.DEBUGGER, annotations, null);
    }

    static ParameterDeclaration missing(String reason) {
        return new ParameterDeclaration(Kind.MISSING, Collections.<AnnotationEntry>emptyList(), reason);
    }
}
