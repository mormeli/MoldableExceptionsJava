package ch.moldexj.demos.constraints;

import ch.moldexj.view.DebugView;
import ch.moldexj.view.DebugViews;
import ch.moldexj.view.annotation.Exception;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Collection;
import java.util.Map;

public class ConstraintViolationExceptionViews {
    private record Tuple<A, B>(A a, B b) {}

    @DebugView(value = "Violations", priority = 10)
    static DebugViews.View constraintViolationView(@Exception ConstraintViolationException ex) {
        return DebugViews.styledTable(ex.getConstraintViolations(),
                violation -> null,
                DebugViews.column("Field", cv -> cv.getPropertyPath().toString()),
                DebugViews.column("Violated Constraint", ConstraintViolationExceptionViews::formatConstraintDescriptor),
                DebugViews.column("Value", ConstraintViolation::getInvalidValue));
    }

    static <T> String formatConstraintDescriptor(ConstraintViolation<T> constraintViolation) {
        return switch (constraintViolation.getConstraintDescriptor().getAnnotation()) {
            case Size s -> "%s <= %s <= %s".formatted(s.min(), formatMagnitudeByType(constraintViolation), s.max());
            case Min m -> "%s < %s".formatted(m.value(), formatMagnitudeByType(constraintViolation));
            default -> "N/A";
        };
    }

    static String formatMagnitudeByType(ConstraintViolation<?> constraintViolation) {
        final String pattern = switch (constraintViolation.getInvalidValue()) {
            case Number n -> "%s";
            case String s -> "length(\"%s\")";
            case Collection<?> l -> "size(%s)";
            case Map<?,?> m -> "size(%s)";
            default -> "N/A";
        };
        return pattern.formatted(constraintViolation.getPropertyPath().toString());
    }
}
