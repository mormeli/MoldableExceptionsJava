package dev.moldable.exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory methods for the view kinds the IntelliJ plugin understands: text, diff and table.
 *
 * <p>The plugin reads the returned objects by reflection, through the accessor methods {@code kind()},
 * {@code text()}, {@code expected()}, {@code actual()}, {@code expectedLabel()}, {@code actualLabel()},
 * {@code columns()}, {@code rows()}, {@code rowStyles()}, {@code spans()} and {@code caption()}. Any object with that
 * shape works.
 *
 * <p>Styles, used by highlighted text and table rows: {@code "error"}, {@code "warning"}, {@code "ok"},
 * {@code "muted"}, {@code "strong"}, and {@code "accent1"} to {@code "accent6"} (distinct colors, e.g. for nesting
 * levels). The plugin maps them to colors that work in light and dark themes.
 */
public final class DebugViews {

    private DebugViews() {
    }

    /** Common type of the view descriptions. */
    public interface View {
        /** "text", "diff" or "table". */
        String kind();
    }

    /** A block of selectable, monospaced text. */
    public static View text(String text) {
        return new TextView(text);
    }

    /** A side-by-side diff between an expected and an actual text, shown in IntelliJ's diff viewer. */
    public static View diff(String expected, String actual) {
        return new DiffView(expected, actual, "Expected", "Actual");
    }

    /** A side-by-side diff with custom labels for both sides. */
    public static View diff(String expected, String actual, String expectedLabel, String actualLabel) {
        return new DiffView(expected, actual, expectedLabel, actualLabel);
    }

    /**
     * Text shown in an editor viewer, with styled ranges and tooltips: the "show, don't draw with characters" view.
     *
     * <pre>{@code
     * return DebugViews.highlight(pattern)
     *         .span(0, 1, "accent1", "group, closed at 5")
     *         .span(1, 2, "error", "Illegal repetition")
     *         .caption("Brackets are colored by nesting depth.");
     * }</pre>
     */
    public static HighlightView highlight(String text) {
        return new HighlightView(text);
    }

    /** Like {@link #table}, plus a style per row ({@code "error"}, {@code "ok"}, {@code "muted"}, ... or null). */
    @SafeVarargs
    public static <T> View styledTable(Iterable<? extends T> items, Cell<? super T> rowStyle, Column<? super T>... columns) {
        TableView table = (TableView) table(items, columns);
        List<String> styles = new ArrayList<>();
        for (T item : items) {
            Object style = rowStyle.apply(item);
            styles.add(style == null ? "" : String.valueOf(style));
        }
        return new TableView(table.columns, table.rows, styles);
    }

    /** A table with one row per item; each column turns an item into a cell. */
    @SafeVarargs
    public static <T> View table(Iterable<? extends T> items, Column<? super T>... columns) {
        List<String> headers = new ArrayList<>();
        for (Column<? super T> column : columns) {
            headers.add(column.header);
        }
        List<List<String>> rows = new ArrayList<>();
        for (T item : items) {
            List<String> row = new ArrayList<>();
            for (Column<? super T> column : columns) {
                Object cell = column.cell.apply(item);
                row.add(cell == null ? "" : String.valueOf(cell));
            }
            rows.add(row);
        }
        return new TableView(headers, rows);
    }

    /** A table column: a header and a function from an item to its cell. */
    public static <T> Column<T> column(String header, Cell<? super T> cell) {
        return new Column<>(header, cell);
    }

    /** Java 8 has java.util.function.Function, but a dedicated type keeps call sites readable. */
    @FunctionalInterface
    public interface Cell<T> {
        Object apply(T item);
    }

    public static final class Column<T> {
        private final String header;
        private final Cell<? super T> cell;

        private Column(String header, Cell<? super T> cell) {
            this.header = header;
            this.cell = cell;
        }
    }

    public static final class TextView implements View {
        private final String text;

        TextView(String text) {
            this.text = text;
        }

        @Override
        public String kind() {
            return "text";
        }

        public String text() {
            return text;
        }
    }

    public static final class DiffView implements View {
        private final String expected;
        private final String actual;
        private final String expectedLabel;
        private final String actualLabel;

        DiffView(String expected, String actual, String expectedLabel, String actualLabel) {
            this.expected = expected;
            this.actual = actual;
            this.expectedLabel = expectedLabel;
            this.actualLabel = actualLabel;
        }

        @Override
        public String kind() {
            return "diff";
        }

        public String expected() {
            return expected;
        }

        public String actual() {
            return actual;
        }

        public String expectedLabel() {
            return expectedLabel;
        }

        public String actualLabel() {
            return actualLabel;
        }
    }

    public static final class TableView implements View {
        private final List<String> columns;
        private final List<List<String>> rows;
        private final List<String> rowStyles;

        TableView(List<String> columns, List<List<String>> rows) {
            this(columns, rows, Collections.<String>emptyList());
        }

        TableView(List<String> columns, List<List<String>> rows, List<String> rowStyles) {
            this.columns = Collections.unmodifiableList(columns);
            this.rows = Collections.unmodifiableList(rows);
            this.rowStyles = Collections.unmodifiableList(rowStyles);
        }

        public List<String> rowStyles() {
            return rowStyles;
        }

        @Override
        public String kind() {
            return "table";
        }

        public List<String> columns() {
            return columns;
        }

        public List<List<String>> rows() {
            return rows;
        }

        @Override
        public String toString() {
            return columns + " " + Arrays.toString(rows.toArray());
        }
    }

    /** Highlighted text: see {@link DebugViews#highlight(String)}. */
    public static final class HighlightView implements View {
        private final String text;
        private final List<Span> spans = new ArrayList<>();
        private String caption = "";

        HighlightView(String text) {
            this.text = text == null ? "" : text;
        }

        /** Styles the characters from {@code start} (inclusive) to {@code end} (exclusive); the tooltip may be null. */
        public HighlightView span(int start, int end, String style, String tooltip) {
            int from = Math.max(0, Math.min(start, text.length()));
            int to = Math.max(from, Math.min(end, text.length()));
            if (to > from) {
                spans.add(new Span(from, to, style, tooltip));
            }
            return this;
        }

        /** A short explanation shown under the text. */
        public HighlightView caption(String caption) {
            this.caption = caption == null ? "" : caption;
            return this;
        }

        @Override
        public String kind() {
            return "highlight";
        }

        public String text() {
            return text;
        }

        public List<Span> spans() {
            return Collections.unmodifiableList(spans);
        }

        public String caption() {
            return caption;
        }
    }

    public static final class Span {
        private final int start;
        private final int end;
        private final String style;
        private final String tooltip;

        Span(int start, int end, String style, String tooltip) {
            this.start = start;
            this.end = end;
            this.style = style;
            this.tooltip = tooltip;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public String style() {
            return style;
        }

        public String tooltip() {
            return tooltip;
        }
    }
}
