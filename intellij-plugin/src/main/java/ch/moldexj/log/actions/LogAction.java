package ch.moldexj.log.actions;

import ch.moldexj.log.MoldexLogPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/** Base of the actions in the toolbar of the Moldable Log tool window: each acts on the {@link MoldexLogPanel} it belongs to. */
abstract class LogAction extends DumbAwareAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // The panel's list and selection are only touched on the Swing thread.
        return ActionUpdateThread.EDT;
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
        MoldexLogPanel panel = e.getData(MoldexLogPanel.KEY);
        e.getPresentation().setEnabled(panel != null && isEnabled(panel));
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        MoldexLogPanel panel = e.getData(MoldexLogPanel.KEY);
        if (panel != null) {
            perform(panel);
        }
    }

    protected abstract boolean isEnabled(MoldexLogPanel panel);

    protected abstract void perform(MoldexLogPanel panel);
}
