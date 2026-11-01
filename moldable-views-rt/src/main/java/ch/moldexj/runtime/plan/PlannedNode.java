package ch.moldexj.runtime.plan;

import java.util.ArrayList;
import java.util.List;

/** One exception of the chain, with the views planned for it. */
public final class PlannedNode {

    public final Throwable exception;
    /** "Exception", "Cause", "Suppressed[0]". */
    public final String label;
    public final int depth;
    public final boolean isRoot;
    public final List<PlannedView> views = new ArrayList<>();

    public PlannedNode(Throwable exception, String label, int depth, boolean isRoot) {
        this.exception = exception;
        this.label = label;
        this.depth = depth;
        this.isRoot = isRoot;
    }
}
