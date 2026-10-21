package demo.ee;

import dev.moldable.exceptions.DebugView;
import dev.moldable.exceptions.DebugViews;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import javax.swing.*;
import java.util.stream.Collectors;

public class ConstraintViolationExceptionViews {
    @DebugView(value = "Violations")
    static DebugViews.View pattern(ConstraintViolationException ex) {
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
