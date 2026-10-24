package ch.moldexj.actions;

import ch.moldexj.debugger.MoldableDebugProcessListener;
import ch.moldexj.debugger.MoldableExceptionDebuggerManagerListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.jetbrains.annotations.NotNull;

/** Run → "Moldable: Open Views at Exception Breakpoints" (on by default). */
public final class AutoOpenToggleAction extends DumbAwareToggleAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return MoldableDebugProcessListener.isEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(MoldableExceptionDebuggerManagerListener.ENABLED_KEY, state, true);
    }
}
