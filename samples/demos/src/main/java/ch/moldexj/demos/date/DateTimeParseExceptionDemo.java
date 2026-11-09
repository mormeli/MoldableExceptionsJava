package ch.moldexj.demos.date;

import java.time.LocalDate;
import java.util.Scanner;

/**
 * A DateTimeParseException that nothing catches. Just debug it (no breakpoint needed): the debugger stops inside
 * java.time where the exception is thrown, and the plugin opens the views from {@link DateTimeParseExceptionViews}.
 */
public final class DateTimeParseExceptionDemo {

    public static void main(String[] args) {
        // 1. Read a date
        Scanner scanner = new Scanner(System.in);
        System.out.println("What date would you like the weekday of?");
        System.out.print("> ");
        String day = scanner.nextLine();

        // 2. Parse the date
        LocalDate localDate = LocalDate.parse(day);
        System.out.printf("The %s is a %s%n", localDate, localDate.getDayOfWeek());
    }

}
