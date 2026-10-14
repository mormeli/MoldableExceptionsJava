package dev.moldable.intellij;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * "Show Moldable Views" in the context menu of the Variables, Watches and Evaluate views: opens the views of the
 * selected exception (and of its causes and suppressed exceptions).
 */
public final class ShowMoldableViewsAction extends XDebuggerTreeActionBase {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
        // Only a cheap check here; whether it is a Throwable is checked on the debugger thread.
        return objectOf(node) != null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(e.getPresentation().isEnabled());
    }

    @Override
    protected void perform(@NotNull XValueNodeImpl node, @NotNull String nodeName, @NotNull AnActionEvent e) {
        Project project = e.getProject();
        ObjectReference exception = objectOf(node);
        if (project == null || exception == null) {
            return;
        }
        EvaluationContextImpl context = ((JavaValue) node.getValueContainer()).getEvaluationContext();
        context.getManagerThread().schedule(
                new SnapshotCommand(project, context.getSuspendContext(), context, exception, nodeName, false));
    }

    private static ObjectReference objectOf(XValueNodeImpl node) {
        XValueContainer container = node.getValueContainer();
        if (!(container instanceof JavaValue)) {
            return null;
        }
        Value value = ((JavaValue) container).getDescriptor().getValue();
        return value instanceof ObjectReference ? (ObjectReference) value : null;
    }
}
