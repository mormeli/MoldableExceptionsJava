package ch.moldexj.view.finder;

import ch.moldexj.view.Parameter;
import ch.moldexj.view.View;
import ch.moldexj.view.request.DataRequestArguments;
import ch.moldexj.view.request.DataRequestRegistry;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.function.Function;

public class PsiViewFinder2 implements ViewFinder {
    private static Logger LOG = Logger.getInstance(PsiViewFinder2.class);

    private final Project project;
    private final DataRequestRegistry dataRequestRegistry;
    private PsiClass debugViewAnnotation;

    public PsiViewFinder2(@NotNull Project project, @NotNull DataRequestRegistry requestRegistry) {
        Objects.requireNonNull(project);
        Objects.requireNonNull(requestRegistry);

        this.project = project;
        this.dataRequestRegistry = requestRegistry;
    }

    /**
     * @return
     */
    @Override
    public Collection<View> findViews() {
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return Collections.emptyList();
        }

        try {
            return ReadAction.compute(() -> {
                GlobalSearchScope globalSearchScope = GlobalSearchScope.allScope(project);
                PsiClass debugViewAnnotation = findDebugViewAnnotation(globalSearchScope);

                if (debugViewAnnotation == null) {
                    LOG.warn("Cannot find debug view annotation");
                    return Collections.emptyList();
                }

                return AnnotatedElementsSearch.searchPsiMethods(debugViewAnnotation, globalSearchScope)
                        .allowParallelProcessing()
                        .filtering(e -> e.hasModifierProperty(PsiModifier.STATIC) && e.getParameterList().getParametersCount() >= 1)
                        .mapping(this::transformMethodToView)
                        .filtering(Optional::isPresent)
                        .mapping(Optional::get)
                        .findAll();
            });
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("Moldable Exceptions: could not search for external views", e);
            return Collections.emptyList();
        }
    }

    private Optional<View> transformMethodToView(PsiMethod psiMethod) {
        List<Parameter> parameters = new  ArrayList<>(psiMethod.getParameterList().getParametersCount());
        int i = 0;
        for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
            var request = Arrays.stream(psiParameter.getAnnotations()).map(dataRequestRegistry::createArguments)
                    .findFirst()
                    .flatMap(Function.identity());
            if (request.isEmpty())
                return Optional.empty();
            parameters.add(new Parameter(i++, request.get()));
        }

        return Optional.of(new View(
                ClassUtil.getJVMClassName(psiMethod.getContainingClass()),
                psiMethod.getName(),
                parameters
        ));
    }

    private PsiClass findDebugViewAnnotation(GlobalSearchScope searchScobe) {
        if (debugViewAnnotation == null) {
            PsiClass[] debugViews = PsiShortNamesCache.getInstance(project).getClassesByName("DebugView", searchScobe);

            if (debugViews.length == 0) {
                LOG.warn("No debug view annotation found in project " + project.getName());
                return null;
            }

            if (debugViews.length > 1) {
                LOG.warn("Found more than one DebugView annotation found in project " + project.getName());
            }
            debugViewAnnotation = debugViews[0];
        }
        return debugViewAnnotation;
    }
}
