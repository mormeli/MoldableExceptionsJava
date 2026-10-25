package ch.moldexj.demos.ee;

import ch.moldexj.view.context.DebugFrame;
import ch.moldexj.view.context.DebugLocal;
import ch.moldexj.view.context.DebugStack;
import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;
import ch.moldexj.view.annotation.Exception;
import ch.moldexj.view.annotation.StackFrames;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class ConstraintViolationExceptionViews {
    @DebugView(value = "Violations", priority = 10)
    static DebugViews.View constraintViolationView(@Exception ConstraintViolationException ex) {
        return DebugViews.styledTable(
                ex.getConstraintViolations(),
                violation -> null,
                DebugViews.column("Field", cv -> cv.getPropertyPath().toString()),
                DebugViews.column("Violated Constraint", cv ->
                    switch (cv.getConstraintDescriptor().getAnnotation()) {
                        case Size s -> "%s <= size(%s) <= %s".formatted(s.min(), cv.getPropertyPath().toString(), s.max());
                        case Min m -> "%s < size(%s)".formatted(m.value(), cv.getPropertyPath().toString());
                        default -> "N/A";
                    }
                ),
                DebugViews.column("Value", ConstraintViolation::getInvalidValue));
    }

    /**
     * The validated object, taken live from the stack of the throw: the exception only knows the violations, the
     * debugger also knows which object was validated and what else was in scope.
     */
    @DebugView(value = "Throw site", priority = 20)
    static DebugViews.View throwSite(
            @Exception ConstraintViolationException ex,
            @StackFrames(depth = 3) DebugStack stack) {
        List<String[]> rows = new ArrayList<>();
        for (DebugFrame frame : stack.frames()) {
            for (DebugLocal local : frame.locals()) {
                // value() is the live object from the paused program, so it can be checked and used like any other.
                String validated = local.value() instanceof User ? "validated" : "";
                rows.add(new String[]{frame.methodName() + ":" + frame.line(), local.name(), local.typeName(), local.text(), validated});
            }
        }
        return DebugViews.styledTable(rows,
                row -> row[4].isEmpty() ? null : "warning",
                DebugViews.column("Frame", row -> row[0]),
                DebugViews.column("Variable", row -> row[1]),
                DebugViews.column("Type", row -> row[2]),
                DebugViews.column("Value", row -> row[3]));
    }
}
