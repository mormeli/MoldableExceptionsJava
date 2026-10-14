package dev.moldable.intellij;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSON produced by {@code dev.moldable.intellij.rt.MoldableSnapshot} in the debugged JVM, as parsed by Gson.
 * Public fields on purpose: these are plain data holders.
 */
public final class ExceptionSnapshot {

    public List<Node> chain = new ArrayList<>();
    public List<String> notes = new ArrayList<>();

    /** The node to open first: the first exception that declares its own views, else the outermost one. */
    public int initialIndex() {
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).moldable) {
                return i;
            }
        }
        return 0;
    }

    public boolean hasMoldable() {
        for (Node node : chain) {
            if (node.moldable) {
                return true;
            }
        }
        return false;
    }

    public static final class Node {
        /** "Exception", "Cause", "Suppressed[0]". */
        public String label = "";
        public int depth;
        public String type = "";
        public String message = "";
        public boolean moldable;
        public List<View> views = new ArrayList<>();

        public String shortType() {
            int dot = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
            return dot < 0 ? type : type.substring(dot + 1);
        }
    }

    public static final class View {
        public String title = "";
        /** "text", "diff", "table", "highlight" or "error". */
        public String kind = "text";
        public boolean custom;
        public int priority;
        public String text;
        public String expected;
        public String actual;
        public String expectedLabel;
        public String actualLabel;
        public List<String> columns;
        public List<List<String>> rows;
        /** Tables: one style per row ("error", "ok", "muted", ...; "" for none). */
        public List<String> rowStyles;
        /** Highlighted text: styled ranges, and a caption shown under the text. */
        public List<Span> spans;
        public String caption;
    }

    public static final class Span {
        public int start;
        public int end;
        /** "error", "warning", "ok", "muted", "strong", "accent1" … "accent6". */
        public String style;
        public String tooltip;
    }
}
