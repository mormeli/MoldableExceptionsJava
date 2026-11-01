package ch.moldexj.runtime.plan;

import java.util.*;

/**
 * Walks the exception, its causes and suppressed exceptions, depth first, and makes one node per exception.
 * Guards against cycles, very long chains and broken {@code getCause()} overrides.
 */
public final class ChainWalker {
    private static final int NODE_LIMIT = 32;
    private static final int DEPTH_LIMIT = 12;

    private final Throwable root;
    private final List<String> diagnostics;
    private final List<PlannedNode> nodes = new ArrayList<>();
    private final Map<Throwable, Boolean> seen = new IdentityHashMap<>();

    public ChainWalker(Throwable root, List<String> diagnostics) {
        this.root = root;
        this.diagnostics = diagnostics;
    }

    /***
     * Walks through the throwable collecting all causes and suppressed exceptions on its way.
     * @return a list of
     */
    public List<PlannedNode> walk() {
        try {
            visit(root, "Exception", 0);
        } catch (Throwable ex) {
            diagnostics.add("An exception has been thrown while analyzing the exception chain: " + ex.getMessage());
        }
        return nodes;
    }

    private void visit(Throwable throwable, String label, int depth) {
        if (throwable == null) {
            return;
        }
        if (seen.containsKey(throwable)) {
            diagnostics.add(label + " refers back to an exception already shown (cycle); skipped.");
            return;
        }
        if (depth > DEPTH_LIMIT || nodes.size() >= NODE_LIMIT) {
            diagnostics.add("Stopped at " + label + ": more than " + NODE_LIMIT + " exceptions or nesting deeper than " + DEPTH_LIMIT + ".");
            return;
        }
        seen.put(throwable, true);
        nodes.add(new PlannedNode(throwable, label, depth, throwable == root));

        Throwable cause = throwable.getCause();
        if (cause != throwable) {
            visit(cause, "Cause", depth + 1);
        }

        Throwable[] suppressed = throwable.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            visit(suppressed[i], "Suppressed[" + i + "]", depth + 1);
        }
    }
}
