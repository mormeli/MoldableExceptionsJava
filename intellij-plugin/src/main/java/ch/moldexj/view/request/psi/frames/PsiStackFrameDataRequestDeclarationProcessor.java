package ch.moldexj.view.request.psi.frames;

import ch.moldexj.view.StackFrames;
import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.psi.PsiAnnotationDataRequestDeclarationProcessor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import org.jetbrains.annotations.NotNull;

public class PsiStackFrameDataRequestDeclarationProcessor extends PsiAnnotationDataRequestDeclarationProcessor<StackFrames> {
    public PsiStackFrameDataRequestDeclarationProcessor() {
        super(StackFrames.class);
    }

    /**
     * @param declaration
     * @return
     */
    @Override
    public DataRequestArguments createRequest(@NotNull PsiAnnotation declaration) {
        assertAnnotationType(declaration);
        PsiAnnotationMemberValue value = declaration.findAttributeValue("depth");
        Object depthObj = JavaPsiFacade.getInstance(declaration.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(value);
        if (!(depthObj instanceof Integer depth))
            throw new IllegalStateException("depth must be an integer");
        return new StackFramesDataRequestArguments(PsiStackFrameDataRequestProcessor.class, depth);
    }
}

