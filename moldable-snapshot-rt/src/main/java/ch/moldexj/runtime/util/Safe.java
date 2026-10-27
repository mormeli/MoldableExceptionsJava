package ch.moldexj.runtime.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Text from objects of the debugged program, which may throw from any method. */
public final class Safe {
    private static final int TEXT_LENGTH_LIMIT = 100_000;

    private Safe() {
    }

    public static String message(Throwable t) {
        try {
            String message = t.getMessage();
            return message == null ? "" : message;
        } catch (Throwable e) {
            return "(getMessage() threw " + e.getClass().getName() + ")";
        }
    }

    public static String toString(Object o) {
        try {
            return String.valueOf(o);
        } catch (Throwable e) {
            return "(toString() threw " + e.getClass().getName() + ")";
        }
    }

    /** Type, message and stack trace, for error tabs and notes. */
    public static String describe(Throwable e) {
        StringWriter writer = new StringWriter();
        writer.append(e.getClass().getName()).append(": ").append(message(e)).append("\n\n");
        try {
            e.printStackTrace(new PrintWriter(writer));
        } catch (Throwable ignored) {
            // the type and message are enough
        }
        return writer.toString();
    }

    public static String truncate(String text) {
        return text.length() <= TEXT_LENGTH_LIMIT ? text
                : text.substring(0, TEXT_LENGTH_LIMIT) + "\n… truncated (" + text.length() + " characters in total)";
    }
}
