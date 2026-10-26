package ch.moldexj.view.finder;

import ch.moldexj.view.View;
import ch.moldexj.view.request.DataRequestRegistry;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All views with parameters the IDE knows: the project's sources ({@link PsiViewFinder}) and the manifests of its
 * libraries ({@link ManifestViewFinder}). Sources win over a manifest entry of the same method.
 *
 * <p>Cached until code or the project's libraries change, so an exception stop does not search the project. The helper
 * in the debugged JVM still reads the manifests on the program's classpath, for views the IDE does not know.
 */
@Service(Service.Level.PROJECT)
public final class MoldableExceptionsViewIndex {
    private static final Logger LOG = Logger.getInstance(MoldableExceptionsViewIndex.class);

    private final Project project;
    private final DataRequestRegistry registry = new DataRequestRegistry();

    public MoldableExceptionsViewIndex(@NotNull Project project) {
        this.project = project;
    }

    public static MoldableExceptionsViewIndex getInstance(@NotNull Project project) {
        return project.getService(MoldableExceptionsViewIndex.class);
    }

    /** Safe on the debugger thread. Empty while the IDE is indexing or if the search fails. */
    public @NotNull List<View> views() {
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return List.of();
        }
        try {
            return ReadAction.compute(() -> CachedValuesManager.getManager(project).getCachedValue(project,
                    () -> CachedValueProvider.Result.create(compute(),
                            PsiModificationTracker.getInstance(project), ProjectRootManager.getInstance(project))));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (IndexNotReadyException e) {
            return List.of(); // indexing started in the meantime
        } catch (RuntimeException e) {
            LOG.warn("Moldable Exceptions: could not search for views", e);
            return List.of();
        }
    }

    private List<View> compute() {
        Map<String, View> byKey = new LinkedHashMap<>();
        for (View view : new PsiViewFinder(project, registry).findViews()) {
            byKey.put(view.key(), view);
        }
        for (View view : new ManifestViewFinder(project, registry).findViews()) {
            byKey.putIfAbsent(view.key(), view);
        }
        return List.copyOf(new ArrayList<>(byKey.values()));
    }
}
