package ch.moldexj.demos.ee;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;
import ch.moldexj.view.Exception;
import ch.moldexj.view.StackFrames;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class ConstraintViolationExceptionViews {
    @DebugView(value = "Violations")
    static DebugViews.View constraintViolationView(
            @Exception ConstraintViolationException ex,
            @StackFrames(depth = 1) Object stackFrames) {
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
}
