package ch.moldexj.view.request.psi;

import ch.moldexj.view.request.*;
import ch.moldexj.view.request.frames.StackFramesDataRequestProcessor;
import org.jetbrains.annotations.NotNull;

public class PsiQueryDataRequestDeclarationProcessor implements DataRequestDeclarationProcessor {
    private static final PsiQueryDataRequestProcessor PROCESSOR = new PsiQueryDataRequestProcessor();

    /**
     * @param annotation
     * @return
     */
    @Override
    public boolean accept(@NotNull AnnotationData annotation) {
        return annotation.simpleName().equals("PsiQuery");
    }

    /**
     * @param annotation
     * @return
     */
    @Override
    public @NotNull ParameterSource createSource(@NotNull AnnotationData annotation) {
        return new ParameterSource.Provided(
                new PsiQueryDataRequestArguments((String) annotation.attribute("query")),
                PROCESSOR);
    }
}
