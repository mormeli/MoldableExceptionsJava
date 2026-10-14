package dev.moldable.intellij;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.jetbrains.annotations.NotNull;

/** Run → "Moldable: Stop at Uncaught Moldable Exceptions" (on by default). Takes effect immediately. */
public final class StopOnUncaughtToggleAction extends DumbAwareToggleAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return MoldableUncaughtRequestor.isEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(MoldableUncaughtRequestor.ENABLED_KEY, state, true);
    }
}
