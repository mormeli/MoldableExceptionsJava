package ch.moldexj.runtime.plan;

import ch.moldexj.runtime.view.ViewMethod;

/** A view planned for one exception, with how each of its parameters is filled for that exception. */
public final class PlannedView {

    public final ViewMethod view;
    public final Binding[] bindings;

    public PlannedView(ViewMethod view, Binding[] bindings) {
        this.view = view;
        this.bindings = bindings;
    }
}
