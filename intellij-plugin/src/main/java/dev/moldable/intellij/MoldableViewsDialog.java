package dev.moldable.intellij;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Shows an {@link ExceptionSnapshot}: the exception chain on the left, the selected exception's views as tabs on the
 * right. The exception's own views come first; Details, Stack trace and Fields are added for every exception.
 * Diffs use IntelliJ's own diff viewer.
 */
final class MoldableViewsDialog extends DialogWrapper {

    /** One auto-opened dialog per project; a new exception stop replaces it. */
    private static final Key<MoldableViewsDialog> CURRENT = Key.create("moldable.exceptions.dialog");

    private final Project project;
    private final ExceptionSnapshot snapshot;
    private final JPanel detailPanel = new JPanel(new BorderLayout());
    private Disposable selectionDisposable;

    static void open(@NotNull Project project, @NotNull ExceptionSnapshot snapshot, @NotNull String origin, boolean replacePrevious) {
        if (replacePrevious) {
            MoldableViewsDialog previous = project.getUserData(CURRENT);
            if (previous != null && !previous.isDisposed()) {
                previous.close(CANCEL_EXIT_CODE);
            }
        }
        MoldableViewsDialog dialog = new MoldableViewsDialog(project, snapshot, origin);
        if (replacePrevious) {
            project.putUserData(CURRENT, dialog);
        }
        dialog.show();
    }

    private MoldableViewsDialog(Project project, ExceptionSnapshot snapshot, String origin) {
        super(project, false);
        this.project = project;
        this.snapshot = snapshot;
        setModal(false);
        ExceptionSnapshot.Node first = snapshot.chain.isEmpty() ? null : snapshot.chain.get(0);
        setTitle("Moldable Exception" + (first == null ? "" : " — " + first.shortType()) + "  (" + origin + ")");
        setOKButtonText("Close");
        init();
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "moldable.exceptions.dialog";
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBList<ExceptionSnapshot.Node> chainList = new JBList<>(snapshot.chain);
        chainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chainList.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends ExceptionSnapshot.Node> list, ExceptionSnapshot.Node node,
                                                 int index, boolean selected, boolean hasFocus) {
                setIpad(JBUI.insets(3, 6 + node.depth * 16, 3, 6));
                append(node.label + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                append(node.shortType(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (node.moldable) {
                    append("  moldable", new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.BLUE));
                }
                setToolTipText(node.type + ": " + node.message);
            }
        });
        chainList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showNode(chainList.getSelectedValue());
            }
        });

        JPanel left = new JPanel(new BorderLayout());
        left.add(ScrollPaneFactory.createScrollPane(chainList), BorderLayout.CENTER);
        if (!snapshot.notes.isEmpty()) {
            JBLabel notes = new JBLabel("<html>" + escape(String.join("\n", snapshot.notes)).replace("\n", "<br>") + "</html>");
            notes.setForeground(UIUtil.getContextHelpForeground());
            notes.setBorder(JBUI.Borders.empty(6));
            left.add(notes, BorderLayout.SOUTH);
        }

        JBSplitter splitter = new JBSplitter(false, 0.28f);
        splitter.setFirstComponent(left);
        splitter.setSecondComponent(detailPanel);
        splitter.setPreferredSize(JBUI.size(1100, 680));

        if (!snapshot.chain.isEmpty()) {
            chainList.setSelectedIndex(snapshot.initialIndex());
        } else {
            detailPanel.add(new JBLabel("The snapshot is empty."), BorderLayout.CENTER);
        }
        return splitter;
    }

    private void showNode(@Nullable ExceptionSnapshot.Node node) {
        if (selectionDisposable != null) {
            Disposer.dispose(selectionDisposable); // releases the diff viewers of the previous selection
        }
        selectionDisposable = Disposer.newDisposable(getDisposable(), "moldable.exceptions.selection");
        detailPanel.removeAll();
        if (node == null) {
            detailPanel.revalidate();
            detailPanel.repaint();
            return;
        }

        JBLabel header = new JBLabel("<html><b>" + escape(node.type) + "</b><br>" + escape(node.message) + "</html>");
        header.setBorder(JBUI.Borders.empty(4, 6, 8, 6));
        header.setCopyable(true);
        detailPanel.add(header, BorderLayout.NORTH);

        JBTabbedPane tabs = new JBTabbedPane();
        for (ExceptionSnapshot.View view : node.views) {
            tabs.addTab(view.title, null, createViewComponent(view), view.custom ? "Declared by the exception" : "Built-in view");
            if (view.custom) {
                // The exception's own views are emphasized; built-in ones (Details, Stack trace, Fields) stay plain.
                JBLabel title = new JBLabel(view.title);
                title.setFont(JBUI.Fonts.label().asBold());
                tabs.setTabComponentAt(tabs.getTabCount() - 1, title);
            }
        }
        detailPanel.add(tabs, BorderLayout.CENTER);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JComponent createViewComponent(ExceptionSnapshot.View view) {
        switch (view.kind) {
            case "diff":
                return createDiff(view);
            case "table":
                return createTable(view.columns, view.rows, view.rowStyles);
            case "highlight":
                return createHighlight(view);
            case "error":
                JComponent error = createText(view.text);
                ((JScrollPane) error).getViewport().getView().setForeground(JBColor.RED);
                return error;
            default:
                return createText(view.text);
        }
    }

    private JComponent createText(@Nullable String text) {
        JBTextArea area = new JBTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
        area.setBorder(JBUI.Borders.empty(6));
        area.setCaretPosition(0);
        return ScrollPaneFactory.createScrollPane(area);
    }

    private JComponent createDiff(ExceptionSnapshot.View view) {
        String expected = view.expected == null ? "" : view.expected;
        String actual = view.actual == null ? "" : view.actual;
        FileType type = guessFileType(expected, actual);
        DiffContentFactory contents = DiffContentFactory.getInstance();
        DocumentContent left = type == null ? contents.create(expected) : contents.create(expected, type);
        DocumentContent right = type == null ? contents.create(actual) : contents.create(actual, type);

        DiffRequestPanel panel = DiffManager.getInstance().createRequestPanel(project, selectionDisposable, getWindow());
        panel.setRequest(new SimpleDiffRequest(view.title, left, right,
                view.expectedLabel == null ? "Expected" : view.expectedLabel,
                view.actualLabel == null ? "Actual" : view.actualLabel));
        return panel.getComponent();
    }

    /** JSON gets syntax highlighting in the diff if the JSON support (bundled with IntelliJ IDEA) is installed. */
    private static @Nullable FileType guessFileType(String expected, String actual) {
        String a = expected.trim();
        String b = actual.trim();
        boolean json = (a.startsWith("{") || a.startsWith("[")) && (b.startsWith("{") || b.startsWith("["));
        if (!json) {
            return null;
        }
        FileType type = FileTypeManager.getInstance().getFileTypeByExtension("json");
        return type instanceof UnknownFileType ? null : type;
    }

    /**
     * Highlighted text in a read-only editor viewer: the editor font and theme, styled ranges, a tooltip for each range,
     * and the caption underneath. Short single-line texts (a regex, a date) are shown larger.
     */
    private JComponent createHighlight(ExceptionSnapshot.View view) {
        String text = StringUtil.convertLineSeparators(view.text == null ? "" : view.text);
        EditorFactory factory = EditorFactory.getInstance();
        Document document = factory.createDocument(text);
        Editor editor = factory.createViewer(document, project);
        Disposer.register(selectionDisposable, () -> factory.releaseEditor(editor));

        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(false);
        settings.setFoldingOutlineShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setAdditionalLinesCount(0);
        settings.setAdditionalColumnsCount(0);
        settings.setRightMarginShown(false);
        settings.setCaretRowShown(false);
        settings.setIndentGuidesShown(false);
        settings.setUseSoftWraps(true);
        if (editor instanceof EditorEx && !text.contains("\n") && text.length() <= 120) {
            EditorEx ex = (EditorEx) editor;
            ex.setFontSize(Math.round(editor.getColorsScheme().getEditorFontSize() * 1.6f));
        }

        List<ExceptionSnapshot.Span> spans = view.spans == null ? List.of() : view.spans;
        for (ExceptionSnapshot.Span span : spans) {
            TextAttributes attributes = ViewStyles.attributes(span.style);
            int start = Math.max(0, Math.min(span.start, text.length()));
            int end = Math.max(start, Math.min(span.end, text.length()));
            if (attributes != null && end > start) {
                editor.getMarkupModel().addRangeHighlighter(start, end, ViewStyles.layer(span.style), attributes,
                        HighlighterTargetArea.EXACT_RANGE);
            }
        }

        // Tooltips: all spans under the mouse, joined (e.g. "unmatched ')'" and the parser's own message).
        editor.addEditorMouseMotionListener(new EditorMouseMotionListener() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent event) {
                String tip = null;
                if (event.isOverText()) {
                    int offset = event.getOffset();
                    StringBuilder tips = new StringBuilder();
                    for (ExceptionSnapshot.Span span : spans) {
                        if (span.tooltip != null && !span.tooltip.isEmpty() && span.start <= offset && offset < span.end) {
                            tips.append(tips.length() == 0 ? "" : "<br>").append(escape(span.tooltip));
                        }
                    }
                    tip = tips.length() == 0 ? null : "<html>" + tips + "</html>";
                }
                editor.getContentComponent().setToolTipText(tip);
            }
        }, selectionDisposable);

        JPanel panel = new JPanel(new BorderLayout());
        JComponent editorComponent = editor.getComponent();
        editorComponent.setBorder(JBUI.Borders.customLine(JBColor.border(), 1));
        panel.add(editorComponent, BorderLayout.CENTER);
        if (view.caption != null && !view.caption.isEmpty()) {
            JBTextArea caption = new JBTextArea(view.caption);
            caption.setEditable(false);
            caption.setLineWrap(true);
            caption.setWrapStyleWord(true);
            caption.setOpaque(false);
            caption.setFont(JBUI.Fonts.label());
            caption.setBorder(JBUI.Borders.empty(10, 4, 4, 4));
            panel.add(caption, BorderLayout.SOUTH);
        }
        return panel;
    }

    private static JComponent createTable(@Nullable List<String> columns, @Nullable List<List<String>> rows,
                                          @Nullable List<String> rowStyles) {
        List<String> headers = columns == null ? List.of() : columns;
        int width = Math.max(1, headers.size());
        Object[][] data = new Object[rows == null ? 0 : rows.size()][width];
        for (int r = 0; r < data.length; r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < width; c++) {
                data[r][c] = c < row.size() ? row.get(c) : "";
            }
        }
        DefaultTableModel model = new DefaultTableModel(data, headers.isEmpty() ? new Object[]{""} : headers.toArray()) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JBTable table = new JBTable(model);
        table.setAutoCreateRowSorter(true);
        if (rowStyles == null || rowStyles.isEmpty()) {
            table.setStriped(true);
        } else {
            // Color whole rows by the style the view gave them (e.g. red for "never closed", green for "accepts").
            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                    if (!selected) {
                        int modelRow = t.convertRowIndexToModel(row);
                        String style = modelRow < rowStyles.size() ? rowStyles.get(modelRow) : null;
                        Color background = ViewStyles.rowBackground(style);
                        Color foreground = ViewStyles.rowForeground(style);
                        c.setBackground(background != null ? background : t.getBackground());
                        c.setForeground(foreground != null ? foreground : t.getForeground());
                    }
                    return c;
                }
            });
        }
        return ScrollPaneFactory.createScrollPane(table);
    }

    private static String escape(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
