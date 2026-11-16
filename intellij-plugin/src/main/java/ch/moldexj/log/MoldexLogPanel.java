package ch.moldexj.log;

import ch.moldexj.view.MoldableViewsDialog;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The content of the tool window "Moldable Log": the exceptions of one log file as a list. Selecting an exception goes
 * to its code and opens its views in the same dialog the debugger uses.
 *
 * <p>The toolbar is the action group {@code Moldable.Log.Toolbar} from plugin.xml; its actions
 * ({@code ch.moldexj.log.actions}) find this panel through {@link #KEY}.
 */
public final class MoldexLogPanel extends SimpleToolWindowPanel {

    public static final DataKey<MoldexLogPanel> KEY = DataKey.create("Moldable.LogPanel");

    private final Project project;
    private final CollectionListModel<MoldexLog.Entry> model = new CollectionListModel<>();
    private final JBList<MoldexLog.Entry> list = new JBList<>(model);
    private final JBLabel status = new JBLabel(" ");
    private @Nullable Path file;
    /** What the status line says about the file; a note about the selected exception is added to it. */
    private String summary = " ";

    public MoldexLogPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.getEmptyText().setText("No log file opened");
        list.getEmptyText().appendLine("Open a log file...", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> chooseFile());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                show(list.getSelectedValue());
            }
        });
        // Selecting the selected exception again changes nothing, so a double click reopens a closed dialog.
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                show(list.getSelectedValue());
                return true;
            }
        }.installOn(list);

        ActionManager actions = ActionManager.getInstance();
        ActionToolbar toolbar = actions.createActionToolbar("MoldableLog", (ActionGroup) actions.getAction("Moldable.Log.Toolbar"), true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        status.setForeground(UIUtil.getContextHelpForeground());
        status.setBorder(JBUI.Borders.empty(4, 8));
        JPanel content = new JPanel(new BorderLayout());
        content.add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContent(content);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        sink.set(KEY, this);
    }

    // ---------------------------------------------------------------- toolbar actions

    public void chooseFile() {
        VirtualFile chosen = FileChooser.chooseFile(FileChooserDescriptorFactory.singleFile(), project, null);
        if (chosen != null) {
            load(chosen.toNioPath());
        }
    }

    public boolean hasFile() {
        return file != null;
    }

    public void reload() {
        if (file != null) {
            load(file);
        }
    }

    public boolean hasPrevious() {
        return list.getSelectedIndex() > 0;
    }

    /** Without a selection, "next" is the first exception. */
    public boolean hasNext() {
        return list.getSelectedIndex() < model.getSize() - 1;
    }

    public void previous() {
        select(list.getSelectedIndex() - 1);
    }

    public void next() {
        select(list.getSelectedIndex() + 1);
    }

    // ---------------------------------------------------------------- loading and showing

    /** Reads the file from disk, not from IntelliJ's file cache: a log may still be written to. */
    private void load(Path path) {
        new Task.Backgroundable(project, "Reading " + path.getFileName(), false) {
            private MoldexLog log;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    log = MoldexLog.read(reader);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void onSuccess() {
                file = path;
                list.clearSelection(); // or an index of the old list would stay selected without being shown
                model.replaceAll(log.entries());
                summary = path.getFileName() + ": " + log.entries().size() + " "
                        + StringUtil.pluralize("exception", log.entries().size())
                        + (log.skipped() == 0 ? "" : ", " + log.skipped() + " unreadable "
                        + StringUtil.pluralize("line", log.skipped()) + " skipped");
                status.setText(summary);
                status.setToolTipText(path.toString());
                list.getEmptyText().setText("No [moldex] lines in " + path.getFileName());
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                Throwable cause = error instanceof UncheckedIOException ? error.getCause() : error;
                status.setText("Could not read " + path.getFileName() + ": " + cause.getMessage());
            }
        }.queue();
    }

    private void select(int index) {
        if (index >= 0 && index < model.getSize()) {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        }
    }

    private void show(@Nullable MoldexLog.Entry entry) {
        if (entry == null || file == null) {
            return;
        }
        status.setText(summary);
        SourceNavigator.navigate(project, entry.snapshot(), place -> {
            if (list.getSelectedValue() == entry) {
                status.setText(summary + "  ·  " + (place != null ? place
                        : entry.root().frames.isEmpty() ? "the log has no stack frames for this exception"
                        : "no stack frame is in this project"));
            }
        });
        MoldableViewsDialog.open(project, entry.snapshot(), file.getFileName() + ":" + entry.line(), true);
    }

    private static final class EntryRenderer extends ColoredListCellRenderer<MoldexLog.Entry> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends MoldexLog.Entry> list, MoldexLog.Entry entry,
                                             int index, boolean selected, boolean hasFocus) {
            var root = entry.root();
            String message = root.message == null ? "" : root.message;
            setIpad(JBUI.insets(3, 6));
            append((index + 1) + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append(root.shortType(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            if (entry.snapshot().hasMoldable()) {
                append("  moldable", new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.BLUE));
            }
            append("  " + message.lines().findFirst().orElse(""), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            setToolTipText(StringUtil.escapeXmlEntities(root.type + ": " + StringUtil.shortenTextWithEllipsis(message, 300, 0))
                    + " (line " + entry.line() + " of the log)");
        }
    }
}
