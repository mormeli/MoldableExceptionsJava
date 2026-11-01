package ch.moldexj.view.pdf.actions;

import ch.moldexj.view.pdf.PdfDiffPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/** Base of the actions in the PDF diff toolbar: each acts on the {@link PdfDiffPanel} the toolbar belongs to. */
abstract class PdfDiffAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // The panel's paging and zoom state is only touched on the Swing thread.
        return ActionUpdateThread.EDT;
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
        PdfDiffPanel panel = e.getData(PdfDiffPanel.KEY);
        e.getPresentation().setEnabled(panel != null && isEnabled(panel));
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        PdfDiffPanel panel = e.getData(PdfDiffPanel.KEY);
        if (panel != null) {
            perform(panel);
        }
    }

    protected boolean isEnabled(PdfDiffPanel panel) {
        return panel.hasPage();
    }

    protected abstract void perform(PdfDiffPanel panel);
}
