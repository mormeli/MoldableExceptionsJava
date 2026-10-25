package ch.moldexj.view.finder;

import ch.moldexj.runtime.manifest.ViewManifest;
import ch.moldexj.view.Parameter;
import ch.moldexj.view.View;
import ch.moldexj.view.request.AnnotationData;
import ch.moldexj.view.request.DataRequestRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds views with parameters in the manifests ({@value ViewManifest#PATH}) that {@code moldable-views-processor}
 * wrote into the project's libraries. Their sources are not needed, and the IDE learns the parameters of views a
 * library ships for its own exceptions.
 */
public final class ManifestViewFinder implements ViewFinder {

    private static final Logger LOG = Logger.getInstance(ManifestViewFinder.class);

    private final Project project;
    private final DataRequestRegistry registry;

    public ManifestViewFinder(@NotNull Project project, @NotNull DataRequestRegistry registry) {
        this.project = project;
        this.registry = registry;
    }

    @Override
    public Collection<View> findViews() {
        List<View> views = new ArrayList<>();
        String fileName = ViewManifest.PATH.substring(ViewManifest.PATH.lastIndexOf('/') + 1);
        for (VirtualFile file : FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.allScope(project))) {
            if (!file.getPath().endsWith(ViewManifest.PATH)) {
                continue;
            }
            try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
                for (ViewManifest.ViewEntry entry : ViewManifest.read(reader)) {
                    if (!entry.params.isEmpty()) {
                        views.add(toView(entry));
                    }
                }
            } catch (IOException e) {
                LOG.warn("Moldable Exceptions: could not read " + file.getPath(), e);
            }
        }
        return views;
    }

    private View toView(ViewManifest.ViewEntry entry) {
        List<Parameter> parameters = new ArrayList<>();
        for (ViewManifest.ParamEntry param : entry.params) {
            List<AnnotationData> annotations = new ArrayList<>();
            for (ViewManifest.AnnotationEntry annotation : param.annotations) {
                annotations.add(new AnnotationData(annotation.name, annotation.attributes));
            }
            parameters.add(new Parameter(param.index, param.type, registry.sourceFor(annotations)));
        }
        return new View(entry.className, entry.methodName, entry.isStatic, parameters);
    }
}
