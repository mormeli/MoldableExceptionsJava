package ch.moldexj.view.request.psi;

import ch.moldexj.view.request.DataRequestContext;
import ch.moldexj.view.request.DataRequestProcessor;
import ch.moldexj.view.request.ProvidedValue;
import com.esotericsoftware.kryo.kryo5.util.Null;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PsiQueryDataRequestProcessor implements DataRequestProcessor<PsiQueryDataRequestArguments> {

    /**
     * @param context
     * @param arguments
     * @return
     */
    @Override
    public @NotNull ProvidedValue process(
            @NotNull DataRequestContext context,
            @NotNull PsiQueryDataRequestArguments arguments) {
        final Project project = context.getEvaluationContext().getProject();

        final ThreadReferenceProxyImpl thread = context.getThrowThread();
        if (thread == null) {
            return ProvidedValue.unavailable("This view needs the stack at the throw of the exception.");
        }

        final DebugProcessImpl process = context.getEvaluationContext().getDebugProcess();
        try {
            for (StackFrameProxyImpl frame : thread.frames()) {
                Location loc = frame.location();
                Optional<SourcePosition> pos = tryGetSourcePosition(process, project, loc);
                if (pos.isPresent()) {
                    return ProvidedValue.of(getSourceLine(pos.get()));
                }
            }
        } catch (EvaluateException e) {
            throw new RuntimeException(e);
        }
        return ProvidedValue.unavailable("Nothing to do.");
    }

    private static @Nullable String getSourceLine(SourcePosition pos) {
        final PsiFile file = pos.getFile();
        Document document = PsiDocumentManager.getInstance(file.getProject())
                .getDocument(file);

        if (document == null || pos.getLine() < 0 || pos.getLine() >= document.getLineCount()) {
            return null;
        }

        return document.getText(new TextRange(
                document.getLineStartOffset(pos.getLine()),
                document.getLineEndOffset(pos.getLine())
        ));
    }

    private static Optional<SourcePosition> tryGetSourcePosition(
            @NotNull DebugProcess process,
            @NotNull Project project,
            @NotNull Location location) {
        return ReadAction.compute(() -> {
            try {
                SourcePosition pos = process.getPositionManager().getSourcePosition(location);
                if (pos == null) {
                    return Optional.empty();
                }
                VirtualFile file = pos.getFile().getVirtualFile();

                if (file == null) {
                    return Optional.empty();
                }

                return Optional.ofNullable(ProjectFileIndex.getInstance(project).isInSourceContent(file) ? pos : null);
            } catch (NoDataException e) {
                // TODO: Solve this better
                throw new RuntimeException(e);
            }
        });
    }
}
