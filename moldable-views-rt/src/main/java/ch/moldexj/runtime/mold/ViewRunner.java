package ch.moldexj.runtime.mold;

import ch.moldexj.runtime.view.ViewMethod;
import ch.moldexj.runtime.plan.Binding;
import ch.moldexj.runtime.plan.PlannedNode;
import ch.moldexj.runtime.plan.PlannedView;
import ch.moldexj.runtime.util.Safe;
import ch.moldexj.runtime.value.Unavailable;
import ch.moldexj.runtime.value.ValueAdapter;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Runs the planned views of one exception with the values from the debugger and writes its node of the snapshot. */
public final class ViewRunner {

    private static final Comparator<PlannedView> ORDER = Comparator.comparing((PlannedView planned) -> planned.view, ViewMethod.ORDER);

    private final List<Object> values;

    /** @param values the values from the debugger, one per request of the plan */
    public ViewRunner(List<Object> values) {
        this.values = values;
    }

    /** The node as JSON: its own views sorted by priority, then the built-in views. */
    public String render(PlannedNode node) {
        List<PlannedView> planned = new ArrayList<>(node.views);
        planned.sort(ORDER);
        List<String> views = new ArrayList<>();
        for (PlannedView view : planned) {
            views.add(run(view, node.exception));
        }
        views.addAll(BuiltInViews.render(node.exception));
        Throwable t = node.exception;
        return "{\"label\":" + Json.string(node.label)
                + ",\"depth\":" + node.depth
                + ",\"type\":" + Json.string(t.getClass().getName())
                + ",\"message\":" + Json.string(Safe.message(t))
                + ",\"moldable\":" + !planned.isEmpty()
                + ",\"views\":" + Json.array(views) + "}";
    }

    private String run(PlannedView planned, Throwable t) {
        ViewMethod view = planned.view;
        Object[] arguments;
        try {
            arguments = arguments(planned, t);
        } catch (CannotRun e) {
            return ViewJson.text(view.title, view.priority, "unavailable", e.getMessage()).toString();
        }
        try {
            view.method.setAccessible(true);
            Object result = view.method.invoke(view.isStatic() ? null : t, arguments);
            return ResultConverter.convert(view.title, view.priority, result);
        } catch (InvocationTargetException e) {
            return error(view, e.getCause() != null ? e.getCause() : e);
        } catch (Throwable e) {
            return error(view, e);
        }
    }

    private Object[] arguments(PlannedView planned, Throwable t) throws CannotRun {
        ViewMethod view = planned.view;
        Class<?>[] types = view.method.getParameterTypes();
        Object[] arguments = new Object[planned.bindings.length];
        for (int i = 0; i < arguments.length; i++) {
            Binding binding = planned.bindings[i];
            switch (binding.kind) {
                case EXCEPTION:
                    arguments[i] = t;
                    break;
                case MISSING:
                    throw new CannotRun(binding.reason);
                case VALUE:
                    Object value = binding.valueIndex < values.size() ? values.get(binding.valueIndex) : null;
                    if (value instanceof Unavailable) {
                        throw new CannotRun(((Unavailable) value).reason);
                    }
                    try {
                        arguments[i] = ValueAdapter.adapt(value, types[i], view.method.getGenericParameterTypes()[i]);
                    } catch (IllegalArgumentException e) {
                        throw new CannotRun("Parameter " + i + ": " + e.getMessage());
                    }
                    break;
            }
        }
        return arguments;
    }

    private static String error(ViewMethod view, Throwable e) {
        return ViewJson.text(view.title, view.priority, "error", "This view threw " + Safe.describe(e)).toString();
    }

    /** A view whose parameters cannot be filled; the message says why. */
    private static final class CannotRun extends Exception {
        CannotRun(String reason) {
            super(reason, null, false, false);
        }
    }
}
