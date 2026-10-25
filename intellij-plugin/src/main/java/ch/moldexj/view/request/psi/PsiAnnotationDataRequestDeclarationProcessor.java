package ch.moldexj.view.request.psi;

import ch.moldexj.view.request.DataRequestDeclarationProcessor;
import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class PsiAnnotationDataRequestDeclarationProcessor<A> implements DataRequestDeclarationProcessor<PsiAnnotation> {
    private final Class<A> annotation;


    protected PsiAnnotationDataRequestDeclarationProcessor(Class<A> annotation) {
        Objects.requireNonNull(annotation);
        this.annotation = annotation;
    }


    @Override
    public boolean accept(Object declarationType) {
        if (declarationType instanceof PsiAnnotation psiAnnotation) {
            return checkQualifiedName(psiAnnotation);
        }
        return false;
    }

    protected void assertAnnotationType(@NotNull PsiAnnotation declaration) {
        if (!checkQualifiedName(declaration)) {
            throw new IllegalArgumentException("Expected argument of type PsiAnnotation");
        }
    }

    private boolean checkQualifiedName(@NotNull PsiAnnotation declaration) {
        return Objects.equals(declaration.getQualifiedName(), annotation.getCanonicalName());
    }
}
