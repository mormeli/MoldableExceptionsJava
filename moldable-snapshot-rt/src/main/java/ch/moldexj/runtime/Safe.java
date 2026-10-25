package ch.moldexj.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Text from objects of the debugged program, which may throw from any method. */
final class Safe {

    private Safe() {
    }

    static String message(Throwable t) {
        try {
            String message = t.getMessage();
            return message == null ? "" : message;
        } catch (Throwable e) {
            return "(getMessage() threw " + e.getClass().getName() + ")";
        }
    }

    static String toString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable e) {
            return "(toString() threw " + e.getClass().getName() + ")";
        }
    }

    /** Type, message and stack trace, for error tabs and notes. */
    static String describe(Throwable e) {
        StringWriter writer = new StringWriter();
        writer.append(e.getClass().getName()).append(": ").append(message(e)).append("\n\n");
        try {
            e.printStackTrace(new PrintWriter(writer));
        } catch (Throwable ignored) {
            // the type and message are enough
        }
        return writer.toString();
    }

    static String truncate(String text) {
        return text.length() <= Limits.TEXT ? text
                : text.substring(0, Limits.TEXT) + "\n… truncated (" + text.length() + " characters in total)";
    }
}
