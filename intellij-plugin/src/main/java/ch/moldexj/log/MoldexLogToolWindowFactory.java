package ch.moldexj.log;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** The tool window "Moldable Log": step through the exceptions a program printed with {@code ExceptionPrinter}. */
public final class MoldexLogToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(new MoldexLogPanel(project), null, false));
    }
}
