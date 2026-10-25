package ch.moldexj.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the exception, its causes and suppressed exceptions, depth first, and writes one node per exception. Guards
 * against cycles, very long chains and broken {@code getCause()} overrides.
 */
final class ChainWalker {

    private final Throwable root;
    private final CustomViews customViews;
    private final List<String> notes;
    private final List<String> nodes = new ArrayList<>();
    private final Map<Throwable, Boolean> seen = new IdentityHashMap<>();

    ChainWalker(Throwable root, CustomViews customViews, List<String> notes) {
        this.root = root;
        this.customViews = customViews;
        this.notes = notes;
    }

    /** The nodes as JSON, in walk order. */
    List<String> walk() {
        visit(root, "Exception", 0);
        return nodes;
    }

    private void visit(Throwable t, String label, int depth) {
        if (t == null) {
            return;
        }
        if (seen.containsKey(t)) {
            notes.add(label + " refers back to an exception already shown (cycle); skipped.");
            return;
        }
        if (depth > Limits.DEPTH || nodes.size() >= Limits.NODES) {
            notes.add("Stopped at " + label + ": more than " + Limits.NODES + " exceptions or nesting deeper than " + Limits.DEPTH + ".");
            return;
        }
        seen.put(t, Boolean.TRUE);

        List<String> views = customViews.render(t, t == root);
        boolean moldable = !views.isEmpty();
        views.addAll(BuiltInViews.render(t));
        nodes.add("{\"label\":" + Json.string(label)
                + ",\"depth\":" + depth
                + ",\"type\":" + Json.string(t.getClass().getName())
                + ",\"message\":" + Json.string(Safe.message(t))
                + ",\"moldable\":" + moldable
                + ",\"views\":" + Json.array(views) + "}");

        Throwable cause = null;
        try {
            cause = t.getCause();
        } catch (Throwable ignored) {
            // a broken getCause() override must not break the debugger
        }
        if (cause != t) {
            visit(cause, "Cause", depth + 1);
        }
        Throwable[] suppressed = new Throwable[0];
        try {
            suppressed = t.getSuppressed();
        } catch (Throwable ignored) {
            // no suppressed exceptions then
        }
        for (int i = 0; i < suppressed.length; i++) {
            visit(suppressed[i], "Suppressed[" + i + "]", depth + 1);
        }
    }
}
