package ch.moldexj.runtime;

import ch.moldexj.runtime.plan.PlannedNode;
import ch.moldexj.runtime.plan.RequestTable;

import java.util.List;

/**
 * What {@link ExceptionMolder#plan} decided: which views run for which exception of the chain, and which debugger data
 * they need. The plugin reads the two public fields with JDI and passes the plan back to {@link ExceptionMolder#mold},
 * which runs exactly these views.
 */
public final class MoldPlan {

    /** True if an exception of the chain has views of its own (built-in views do not count). */
    public final boolean moldable;
    /** The debugger data the views need, as JSON ({@link RequestTable}). */
    public final String requests;

    final Throwable root;
    public final List<PlannedNode> nodes;
    public final List<String> notes;
    public final int requestCount;

    MoldPlan(Throwable root, List<PlannedNode> nodes, List<String> notes, RequestTable requests) {
        this.root = root;
        this.nodes = nodes;
        this.notes = notes;
        this.requests = requests.toJson();
        this.requestCount = requests.size();
        this.moldable = nodes.stream().anyMatch(node -> !node.views.isEmpty());
    }
}
