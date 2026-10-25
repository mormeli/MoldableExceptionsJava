package ch.moldexj.view.request.psi.exception;

import ch.moldexj.view.Exception;
import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.psi.PsiAnnotationDataRequestDeclarationProcessor;
import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;

public class  PsiExceptionDataRequestDeclarationProcessor extends PsiAnnotationDataRequestDeclarationProcessor<Exception> {

    public PsiExceptionDataRequestDeclarationProcessor() {
        super(Exception.class);
    }


    /**
     * @param declaration
     * @return
     */
    @Override
    public DataRequestArguments createRequest(@NotNull PsiAnnotation declaration) {
        assertAnnotationType(declaration);
        return new DataRequestArguments(PsiExceptionDataRequestProcessor.class);
    }
}
