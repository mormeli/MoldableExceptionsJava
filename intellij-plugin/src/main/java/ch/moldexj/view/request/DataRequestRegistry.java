package ch.moldexj.view.request;

import ch.moldexj.view.request.exception.ExceptionDataRequestDeclarationProcessor;
import ch.moldexj.view.request.frames.StackFramesDataRequestDeclarationProcessor;
import ch.moldexj.view.request.psi.PsiQueryDataRequestDeclarationProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** The known parameter annotations. Each declaration processor knows the processor that resolves its requests. */
public final class DataRequestRegistry {

    private final List<DataRequestDeclarationProcessor> processors = new ArrayList<>();

    public DataRequestRegistry() {
        register(new ExceptionDataRequestDeclarationProcessor());
        register(new StackFramesDataRequestDeclarationProcessor());
        register(new PsiQueryDataRequestDeclarationProcessor());
    }

    public void register(@NotNull DataRequestDeclarationProcessor declarationProcessor) {
        processors.add(declarationProcessor);
    }

    /**
     * How to fill a parameter with these annotations: the first annotation a processor accepts decides, so unrelated
     * annotations such as {@code @NotNull} are skipped.
     */
    public @NotNull ParameterSource sourceFor(@NotNull List<AnnotationData> annotations) {
        for (AnnotationData annotation : annotations) {
            for (DataRequestDeclarationProcessor processor : processors) {
                if (processor.accept(annotation)) {
                    return processor.createSource(annotation);
                }
            }
        }
        if (annotations.isEmpty()) {
            return new ParameterSource.Unsupported("A parameter has no annotation, so the plugin cannot fill it.");
        }
        return new ParameterSource.Unsupported("No provider for " + annotations.stream()
                .map(a -> "@" + a.simpleName()).collect(Collectors.joining(", ")) + ".");
    }
}
