package dev.moldable.intellij;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.ClassUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds views for exceptions you don't own: static methods annotated with an annotation named {@code DebugView} that
 * take one parameter, for example
 *
 * <pre>{@code
 * final class JdkExceptionViews {
 *     @DebugView("Parse position")
 *     static String parsePosition(DateTimeParseException e) { ... }
 * }
 * }</pre>
 *
 * The search uses IntelliJ's index of the project's own sources, so nothing has to be registered. The result is a spec
 * of {@code com.acme.JdkExceptionViews#parsePosition} lines; the helper in the debugged JVM loads those classes and
 * calls each method for every exception in the chain that its parameter type accepts.
 */
final class ExternalViewFinder {

    private static final Logger LOG = Logger.getInstance(ExternalViewFinder.class);

    private ExternalViewFinder() {
    }

    /** Safe to call on the debugger thread. Returns "" while indexing or if anything goes wrong. */
    static String find(Project project) {
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return "";
        }
        try {
            return ReadAction.compute(() -> {
                Set<String> found = new LinkedHashSet<>();
                GlobalSearchScope everywhere = GlobalSearchScope.allScope(project);
                GlobalSearchScope projectSources = GlobalSearchScope.projectScope(project);
                for (PsiClass annotation : PsiShortNamesCache.getInstance(project).getClassesByName("DebugView", everywhere)) {
                    if (!annotation.isAnnotationType()) {
                        continue;
                    }
                    for (PsiMethod method : AnnotatedElementsSearch.searchPsiMethods(annotation, projectSources).findAll()) {
                        // The parameter type is checked at runtime (it must be a Throwable matching the exception).
                        if (!method.hasModifierProperty(PsiModifier.STATIC) || method.getParameterList().getParametersCount() != 1) {
                            continue;
                        }
                        PsiClass owner = method.getContainingClass();
                        String jvmName = owner == null ? null : ClassUtil.getJVMClassName(owner);
                        if (jvmName != null) {
                            found.add(jvmName + "#" + method.getName());
                        }
                    }
                }
                return String.join("\n", found);
            });
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Moldable Exceptions: could not search for external views", e);
            return "";
        }
    }
}
