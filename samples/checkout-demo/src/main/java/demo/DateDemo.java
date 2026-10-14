package demo;

import java.time.LocalDate;

/**
 * A DateTimeParseException that nothing catches. Just debug it (no breakpoint needed): the debugger stops inside
 * java.time where the exception is thrown, and the plugin opens the views from {@link JdkExceptionViews}.
 */
public final class DateDemo {

    public static void main(String[] args) {
        String dueDate = "2026-10-8"; // from a CSV export: the day is not zero-padded
        LocalDate due = LocalDate.parse(dueDate);
        System.out.println("Invoice due on " + due);
    }

    private DateDemo() {
    }
}
