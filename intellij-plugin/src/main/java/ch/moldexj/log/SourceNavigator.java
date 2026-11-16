package ch.moldexj.log;

import ch.moldexj.ExceptionSnapshot;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Goes from an exception of a log to the code: the innermost stack frame whose class is in the project's own sources.
 * The frames come from the snapshot ({@code frames}), not from a running program.
 */
final class SourceNavigator {

    private SourceNavigator() {
    }

    /**
     * Opens the frame in the editor without taking the focus.
     *
     * @param result called on the UI thread with the place that was opened ("Demo.java:22"), or null if no frame of
     *               the chain is in the project
     */
    static void navigate(@NotNull Project project, @NotNull ExceptionSnapshot snapshot, @NotNull Consumer<@Nullable String> result) {
        List<ExceptionSnapshot.Frame> frames = frames(snapshot);
        // Finding a class by name needs the indexes, so not on the UI thread and not while indexing.
        ReadAction.nonBlocking(() -> find(project, frames))
                .inSmartMode(project)
                .coalesceBy(SourceNavigator.class, project)
                .finishOnUiThread(ModalityState.defaultModalityState(), place -> {
                    if (place == null) {
                        result.accept(null);
                    } else if (place.file.isValid()) {
                        place.descriptor(project).navigate(false);
                        result.accept(place.file.getName() + (place.line > 0 ? ":" + place.line : ""));
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /** The frames of the exception the dialog opens with, then those of the rest of the chain. */
    private static List<ExceptionSnapshot.Frame> frames(ExceptionSnapshot snapshot) {
        List<ExceptionSnapshot.Frame> frames = new ArrayList<>();
        if (snapshot.chain.isEmpty()) {
            return frames;
        }
        int first = snapshot.initialIndex();
        frames.addAll(snapshot.chain.get(first).frames);
        for (int i = 0; i < snapshot.chain.size(); i++) {
            if (i != first) {
                frames.addAll(snapshot.chain.get(i).frames);
            }
        }
        return frames;
    }

    private static @Nullable Place find(Project project, List<ExceptionSnapshot.Frame> frames) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Set<String> notInProject = new HashSet<>();
        for (ExceptionSnapshot.Frame frame : frames) {
            if (frame == null || frame.className == null) {
                continue;
            }
            // Nested, anonymous and lambda classes (Outer$Inner, Outer$1) are in the file of the outer class.
            int nested = frame.className.indexOf('$');
            String outer = nested < 0 ? frame.className : frame.className.substring(0, nested);
            if (outer.isEmpty() || notInProject.contains(outer)) {
                continue;
            }
            PsiClass psiClass = psi.findClass(outer, scope);
            PsiFile file = psiClass == null ? null : psiClass.getContainingFile();
            VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
            if (virtualFile == null) {
                notInProject.add(outer);
                continue;
            }
            return new Place(virtualFile, frame.line, psiClass.getTextOffset());
        }
        return null;
    }

    /**
     * @param line        1-based line of the frame; not positive if the stack trace has none
     * @param classOffset where the class starts, used without a line
     */
    private record Place(VirtualFile file, int line, int classOffset) {

        OpenFileDescriptor descriptor(Project project) {
            return line > 0
                    ? new OpenFileDescriptor(project, file, line - 1, 0)
                    : new OpenFileDescriptor(project, file, classOffset);
        }
    }
}
