package ch.moldexj.runtime.plan;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the exception, its causes and suppressed exceptions, depth first, and makes one node per exception. Guards
 * against cycles, very long chains and broken {@code getCause()} overrides.
 */
public final class ChainWalker {
    private static final int NODE_LIMIT = 32;
    private static final int DEPTH_LIMIT = 12;

    private final Throwable root;
    private final List<String> notes;
    private final List<PlannedNode> nodes = new ArrayList<>();
    private final Map<Throwable, Boolean> seen = new IdentityHashMap<>();

    public ChainWalker(Throwable root, List<String> notes) {
        this.root = root;
        this.notes = notes;
    }

    /** The nodes in walk order, still without views. */
    public List<PlannedNode> walk() {
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
        if (depth > DEPTH_LIMIT || nodes.size() >= NODE_LIMIT) {
            notes.add("Stopped at " + label + ": more than " + NODE_LIMIT + " exceptions or nesting deeper than " + DEPTH_LIMIT + ".");
            return;
        }
        seen.put(t, Boolean.TRUE);
        nodes.add(new PlannedNode(t, label, depth, t == root));

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
