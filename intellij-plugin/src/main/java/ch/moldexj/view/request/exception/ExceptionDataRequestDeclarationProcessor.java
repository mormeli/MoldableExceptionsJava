package ch.moldexj.view.request.exception;

import ch.moldexj.view.request.AnnotationData;
import ch.moldexj.view.request.DataRequestDeclarationProcessor;
import ch.moldexj.view.request.ParameterSource;
import org.jetbrains.annotations.NotNull;

/**
 * {@code @Exception}: the exception the view is shown for. Matched by simple name, like {@code DebugView}, so a project
 * can declare its own. The value is bound in the debugged JVM, for each exception of the chain.
 */
public final class ExceptionDataRequestDeclarationProcessor implements DataRequestDeclarationProcessor {

    @Override
    public boolean accept(@NotNull AnnotationData annotation) {
        return annotation.simpleName().equals("Exception");
    }

    @Override
    public @NotNull ParameterSource createSource(@NotNull AnnotationData annotation) {
        return new ParameterSource.ThrownException();
    }
}
