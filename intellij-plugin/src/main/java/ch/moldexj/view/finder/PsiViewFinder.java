package ch.moldexj.view.finder;

import ch.moldexj.view.Parameter;
import ch.moldexj.view.View;
import ch.moldexj.view.request.AnnotationData;
import ch.moldexj.view.request.DataRequestRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Finds {@code @DebugView} methods with parameters in the project's own sources, including code that is not compiled
 * yet. Libraries are covered by {@link ManifestViewFinder}. Views without parameters need no declaration: the helper
 * finds them by reflection.
 *
 * <pre>{@code
 * @DebugView("Locals")
 * static String locals(@Exception IllegalStateException e, @StackFrames(depth = 5) DebugStack stack) { ... }
 * }</pre>
 */
public final class PsiViewFinder implements ViewFinder {

    private final Project project;
    private final DataRequestRegistry registry;

    public PsiViewFinder(@NotNull Project project, @NotNull DataRequestRegistry registry) {
        this.project = project;
        this.registry = registry;
    }

    @Override
    public Collection<View> findViews() {
        List<View> views = new ArrayList<>();
        GlobalSearchScope sources = GlobalSearchScope.projectScope(project);
        // Every annotation named DebugView: the one from moldable-views, or a project's own.
        for (PsiClass annotation : PsiShortNamesCache.getInstance(project).getClassesByName("DebugView", GlobalSearchScope.allScope(project))) {
            if (!annotation.isAnnotationType()) {
                continue;
            }
            for (PsiMethod method : AnnotatedElementsSearch.searchPsiMethods(annotation, sources).findAll()) {
                if (method.getParameterList().getParametersCount() > 0) {
                    View view = toView(method);
                    if (view != null) {
                        views.add(view);
                    }
                }
            }
        }
        return views;
    }

    private @Nullable View toView(PsiMethod method) {
        PsiClass owner = method.getContainingClass();
        String className = owner == null ? null : ClassUtil.getJVMClassName(owner);
        if (className == null) {
            return null; // local or anonymous class: the helper could not load it by name
        }
        List<Parameter> parameters = new ArrayList<>();
        PsiParameter[] psiParameters = method.getParameterList().getParameters();
        for (int i = 0; i < psiParameters.length; i++) {
            List<AnnotationData> annotations = new ArrayList<>();
            for (PsiAnnotation annotation : psiParameters[i].getAnnotations()) {
                annotations.add(annotationData(annotation));
            }
            parameters.add(new Parameter(i, jvmTypeName(psiParameters[i].getType()), registry.sourceFor(annotations)));
        }
        return new View(className, method.getName(), method.hasModifierProperty(PsiModifier.STATIC), parameters);
    }

    /** The annotation with all attribute values, defaults included, evaluated as constants. */
    static AnnotationData annotationData(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null) {
            PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            name = reference == null ? "?" : reference.getText(); // unresolved: at least the simple name
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        PsiElement resolved = reference == null ? null : reference.resolve();
        if (resolved instanceof PsiClass annotationClass) {
            for (PsiMethod attribute : annotationClass.getMethods()) {
                Object value = constant(annotation.findAttributeValue(attribute.getName()));
                if (value != null) {
                    attributes.put(attribute.getName(), value);
                }
            }
        }
        return new AnnotationData(name, attributes);
    }

    private static @Nullable Object constant(@Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiClassObjectAccessExpression classLiteral) {
            return jvmTypeName(classLiteral.getOperand().getType());
        }
        if (value instanceof PsiReferenceExpression reference && reference.resolve() instanceof PsiEnumConstant constant) {
            return constant.getName();
        }
        if (value instanceof PsiExpression expression) {
            Object constant = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
                    .computeConstantExpression(expression);
            if (constant instanceof Short || constant instanceof Byte) {
                return ((Number) constant).intValue();
            }
            if (constant instanceof Float) {
                return ((Float) constant).doubleValue();
            }
            if (constant instanceof Character) {
                return constant.toString();
            }
            return constant;
        }
        return null; // arrays and nested annotations are not supported
    }

    /** The erased type as the JVM names it ({@code a.B$C}), like the manifest. */
    static String jvmTypeName(PsiType type) {
        PsiType erased = TypeConversionUtil.erasure(type);
        if (erased instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            String name = resolved == null ? null : ClassUtil.getJVMClassName(resolved);
            if (name != null) {
                return name;
            }
        }
        return erased.getCanonicalText();
    }
}
