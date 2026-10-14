package demo;

import dev.moldable.exceptions.DebugView;
import dev.moldable.exceptions.DebugViews;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Views for exceptions we don't own. Java has no extension methods, so a view for a JDK exception is a static
 * {@code @DebugView} method that takes the exception as its only parameter. The IntelliJ plugin finds these methods in
 * the project's sources and shows them for every exception in the chain that matches the parameter type.
 *
 * <p>This class only has to be on the classpath of the debugged program; nothing calls it at runtime.
 */
final class JdkExceptionViews {

    private JdkExceptionViews() {
    }

    @DebugView(value = "test", priority = 10)
    static DebugViews.View assertException(AssertionError assertionError) {
        return DebugViews.text("Assertion failed with exception: " + assertionError.getMessage());
    }

    /** The input, highlighted: green what was parsed, red where the parser stopped, grey what was never reached. */
    @DebugView(value = "Parse position", priority = 10)
    static DebugViews.View parsePosition(DateTimeParseException e) {
        String input = e.getParsedString();
        if (input == null) {
            return DebugViews.text("(the exception does not carry the input)");
        }
        int index = Math.max(0, e.getErrorIndex());
        boolean atEnd = index >= input.length();
        // At the end of the input there is no character to mark, so show a visible end marker.
        String text = atEnd ? input + " ⟵ end" : input;
        DebugViews.HighlightView view = DebugViews.highlight(text)
                .span(0, Math.min(index, input.length()), "ok", "parsed: \"" + input.substring(0, Math.min(index, input.length())) + "\"");
        if (atEnd) {
            view.span(input.length(), text.length(), "error", "the input ended where more was expected");
        } else {
            view.span(index, index + 1, "error", "the parser stopped here (index " + index + ")")
                .span(index + 1, input.length(), "muted", "never reached");
        }
        return view.caption("Stopped at index " + index + (atEnd ? " (end of input)" : " ('" + input.charAt(index) + "')")
                + ". The formatter that failed is not stored in DateTimeParseException; "
                + "\"Other formats\" shows which patterns accept this input.");
    }

    /** Which common formats would have accepted the input, and what they produce. */
    @DebugView(value = "Other formats", priority = 20)
    static DebugViews.View otherFormats(DateTimeParseException e) {
        String input = e.getParsedString();
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, DateTimeFormatter> format : FORMATS.entrySet()) {
            String result;
            String value;
            try {
                TemporalAccessor parsed = format.getValue().parseBest(input,
                        ZonedDateTime::from, LocalDateTime::from, LocalDate::from, LocalTime::from);
                result = "✓ accepts";
                value = parsed + "  (" + parsed.getClass().getSimpleName() + ")";
            } catch (DateTimeParseException failure) {
                result = "✗ stops at " + failure.getErrorIndex();
                value = "";
            } catch (RuntimeException failure) {
                result = "✗ " + failure.getClass().getSimpleName();
                value = "";
            }
            rows.add(new String[]{format.getKey(), result, value});
        }
        rows.sort((a, b) -> Boolean.compare(!a[1].startsWith("✓"), !b[1].startsWith("✓"))); // matches first
        return DebugViews.styledTable(rows,
                row -> row[1].startsWith("✓") ? "ok" : "muted",
                DebugViews.column("Format", row -> row[0]),
                DebugViews.column("Result", row -> row[1]),
                DebugViews.column("Parsed value", row -> row[2]));
    }

    private static final Map<String, DateTimeFormatter> FORMATS = new LinkedHashMap<>();

    static {
        FORMATS.put("ISO_LOCAL_DATE (yyyy-MM-dd)", DateTimeFormatter.ISO_LOCAL_DATE);
        FORMATS.put("ISO_LOCAL_DATE_TIME", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        FORMATS.put("ISO_OFFSET_DATE_TIME", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        FORMATS.put("yyyy-M-d", DateTimeFormatter.ofPattern("yyyy-M-d"));
        FORMATS.put("d.M.yyyy", DateTimeFormatter.ofPattern("d.M.yyyy"));
        FORMATS.put("dd.MM.yyyy", DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        FORMATS.put("M/d/yyyy", DateTimeFormatter.ofPattern("M/d/yyyy"));
        FORMATS.put("yyyyMMdd (BASIC_ISO_DATE)", DateTimeFormatter.BASIC_ISO_DATE);
    }
}
